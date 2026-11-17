package com.securecall.app.net

import android.media.AudioTrack
import com.securecall.app.audio.jitter.JitterBuffer
import com.securecall.app.crypto.MediaFrameCipher
import com.securecall.app.crypto.MediaSessionV3
import com.securecall.app.crypto.SessionKeyBinding
import com.securecall.app.security.VerifiedCallInvite
import com.securecall.app.ghostnet.media.playback.GhostAudioPlayer
import com.securecall.crypto.CoreCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * STX-24 receive boundary: WebSocket binary and stale DataChannel callbacks must never
 * reach the media receiver. Synthetic keys/frames only; no native library or network.
 */
class CallMediaReceiveGateTest {
    private val sessionId = "00000000-0000-4000-8000-000000000002"
    private val otherSessionId = "00000000-0000-4000-8000-000000000003"

    private class CountingCipher : MediaFrameCipher {
        val opens = AtomicInteger()
        override fun seal(key: ByteArray, senderRole: Int, context: ByteArray, sequence: Long, plaintext: ByteArray) =
            ByteArray(MediaSessionV3.MIN_FRAME)
        override fun open(
            key: ByteArray, expectedPeerRole: Int, context: ByteArray,
            highest: Long, bitmap: Long, frame: ByteArray,
        ): ByteArray? {
            opens.incrementAndGet()
            return null
        }
    }

    private fun media(cipher: MediaFrameCipher, id: String = sessionId): MediaSessionV3 {
        val keys = requireNotNull(SessionKeyBinding.deriveV3(ByteArray(32) { 3 }, "C".repeat(43), "D".repeat(43)))
        return MediaSessionV3(id, false, keys, cipher)
    }

    private fun service(): WebSocketService =
        Mockito.mock(WebSocketService::class.java, Answers.CALLS_REAL_METHODS).also {
            set(it, "audioPlaybackLifecycle", AudioPlaybackLifecycle())
        }

    private fun set(target: WebSocketService, field: String, value: Any?) {
        WebSocketService::class.java.getDeclaredField(field).apply { isAccessible = true }.set(target, value)
    }

    private fun receive(target: WebSocketService, source: WebRtcManager?, id: String, owner: MediaSessionV3) {
        WebSocketService::class.java.getDeclaredMethod(
            "receiveDataChannelMedia", WebRtcManager::class.java, String::class.java,
            MediaSessionV3::class.java, ByteArray::class.java,
        ).apply { isAccessible = true }.invoke(target, source, id, owner, ByteArray(MediaSessionV3.MIN_FRAME))
    }

    @Test
    fun realServiceStopInvalidatesPendingAudioWithoutClosingCurrentMedia() {
        val target = service()
        val lifecycle = AudioPlaybackLifecycle()
        val cipher = CountingCipher()
        val current = media(cipher)
        set(target, "audioPlaybackLifecycle", lifecycle)
        set(target, "mediaSession", current)
        val pending = lifecycle.snapshot()

        target.stopAudioPlayback()

        assertFalse(lifecycle.consume(pending) { error("Stopped audio restarted") })
        assertTrue(lifecycle.consume(lifecycle.snapshot()) {})
        // A stop of ringback/playback must leave the established crypto owner open.
        current.consume(ByteArray(MediaSessionV3.MIN_FRAME)) {}
        assertEquals(1, cipher.opens.get())
        current.close()
    }

    private fun preparedPlayer(track: AudioTrack): GhostAudioPlayer = GhostAudioPlayer().also { player ->
        GhostAudioPlayer::class.java.getDeclaredField("audioTrack").apply {
            isAccessible = true
        }.set(player, track)
        GhostAudioPlayer::class.java.getDeclaredField("isPrepared").apply {
            isAccessible = true
        }.setBoolean(player, true)
    }

    @Test
    fun serviceStopRetiresAndReleasesWhileNativeWriteIsStillBlocked() {
        val target = service()
        val lifecycle = AudioPlaybackLifecycle()
        set(target, "audioPlaybackLifecycle", lifecycle)
        val track = Mockito.mock(AudioTrack::class.java)
        val player = preparedPlayer(track)
        set(target, "audioPlayer", player)
        val generation = lifecycle.snapshot()
        val pcm = ShortArray(960)
        val writing = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val writeReturned = CountDownLatch(1)
        val writeFailure = AtomicReference<Throwable>()
        val playout = AtomicReference<Thread>()
        val pool = Executors.newSingleThreadExecutor()
        Mockito.`when`(track.write(pcm, 0, pcm.size)).thenAnswer {
            playout.set(Thread.currentThread())
            writing.countDown()
            try {
                try {
                    check(finishWrite.await(10, TimeUnit.SECONDS))
                } catch (_: InterruptedException) {
                    // Model a native write: Java interrupt alone does not finish it.
                    check(finishWrite.await(10, TimeUnit.SECONDS))
                }
                480 // stop may interrupt a blocking transfer and return a short write.
            } catch (failure: Throwable) {
                writeFailure.set(failure)
                throw failure
            } finally {
                writeReturned.countDown()
            }
        }
        try {
            JitterBuffer.clear()
            repeat(JitterBuffer.PREFILL) { JitterBuffer.push(pcm) }
            // Exercise the actual service playout thread, not a copy of its lock ordering.
            WebSocketService::class.java.getDeclaredMethod("startJitterPlayback", Any::class.java).apply {
                isAccessible = true
            }.invoke(target, generation)
            check(writing.await(2, TimeUnit.SECONDS))
            val stop = pool.submit { target.stopAudioPlayback() }
            stop.get(2, TimeUnit.SECONDS)
            assertEquals(1L, writeReturned.count)
            Mockito.verify(track).stop()
            Mockito.verify(track).release()
            assertFalse(lifecycle.consume(generation) { error("Old worker dequeued again") })

            // Even an old worker delayed between dequeue and write cannot auto-prepare/play.
            player.prepare()
            player.play()
            player.write(pcm)
            player.retire()
            Mockito.verify(track, Mockito.times(1)).play()
            Mockito.verify(track, Mockito.times(1)).write(pcm, 0, pcm.size)
            Mockito.verify(track, Mockito.times(1)).release()

            val replacementTrack = Mockito.mock(AudioTrack::class.java)
            val replacement = preparedPlayer(replacementTrack)
            set(target, "audioPlayer", replacement)
            assertTrue(lifecycle.consume(lifecycle.snapshot()) {})
            replacement.write(pcm)
            Mockito.verify(replacementTrack).play()
            Mockito.verify(replacementTrack).write(pcm, 0, pcm.size)
            set(target, "jitterPlaybackRunning", true)
            JitterBuffer.push(ShortArray(960) { 7 })

            finishWrite.countDown()
            check(writeReturned.await(2, TimeUnit.SECONDS))
            playout.get().join(2_000)
            assertFalse(playout.get().isAlive)
            assertNull(writeFailure.get())
            // Old worker cannot consume fresh jitter frames after its short write returns.
            assertEquals(1, JitterBuffer.size())
            Mockito.verify(track, Mockito.times(1)).write(pcm, 0, pcm.size)
            Mockito.verifyNoMoreInteractions(replacementTrack)
            target.stopAudioPlayback()
            Mockito.verify(replacementTrack).release()
        } finally {
            finishWrite.countDown()
            set(target, "jitterPlaybackRunning", false)
            playout.get()?.let {
                it.interrupt()
                it.join(2_000)
                check(!it.isAlive)
            }
            JitterBuffer.clear()
            pool.shutdownNow()
            check(pool.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun ordinaryReleaseRemainsReusableForThePersistentLegacyRouter() {
        val firstTrack = Mockito.mock(AudioTrack::class.java)
        val player = preparedPlayer(firstTrack)
        player.release()
        Mockito.verify(firstTrack).release()
        // Supply the next prepared track without constructing Android/native resources.
        val nextTrack = Mockito.mock(AudioTrack::class.java)
        GhostAudioPlayer::class.java.getDeclaredField("audioTrack").apply {
            isAccessible = true
        }.set(player, nextTrack)
        GhostAudioPlayer::class.java.getDeclaredField("isPrepared").apply {
            isAccessible = true
        }.setBoolean(player, true)
        val pcm = ShortArray(960)
        player.write(pcm)
        Mockito.verify(nextTrack).play()
        Mockito.verify(nextTrack).write(pcm, 0, pcm.size)
        player.release()
    }

    @Test
    fun webSocketBinaryNeverReachesTheMediaReceiver() {
        val cipher = CountingCipher()
        val target = service()
        set(target, "mediaSession", media(cipher))
        target.onBinaryMessage(ByteArray(MediaSessionV3.MIN_FRAME))
        target.onBinaryMessage(ByteArray(MediaSessionV3.MAX_FRAME))
        assertEquals(0, cipher.opens.get())
    }

    /** Builds the service's private CallSecurityState through its real constructor. */
    private fun securityState(id: String, established: Boolean): Any {
        val stateClass = Class.forName("com.securecall.app.net.CallSecurityState")
        val roleClass = Class.forName("com.securecall.app.net.SecureCallRole")
        val callee = roleClass.enumConstants.first { (it as Enum<*>).name == "CALLEE" }
        val invite = VerifiedCallInvite(
            id, "sc-caller", "sc-caller", "sc-callee", "sc-callee", "synthetic-ephemeral",
            "synthetic-identity", 2_000_000_000L, "synthetic-nonce", "synthetic-signature",
            "synthetic-digest",
        )
        val constructor = stateClass.declaredConstructors.first {
            it.parameterCount == 10 && it.parameterTypes[0] == roleClass
        }.apply { isAccessible = true }
        return constructor.newInstance(
            callee, invite, "synthetic-transcript", null, null, null, true, true, established, "000000",
        )
    }

    @Test
    fun currentEstablishedCallbackReachesReceiverAndEveryStaleVariantIsDropped() {
        Mockito.mockStatic(CoreCrypto::class.java).use { native ->
            var nativeAvailable = true
            native.`when`<Boolean> { CoreCrypto.isNativeAvailable() }.thenAnswer { nativeAvailable }
            val cipher = CountingCipher()
            val target = service()
            val current = media(cipher)
            val currentManager = Mockito.mock(WebRtcManager::class.java)
            val staleManager = Mockito.mock(WebRtcManager::class.java)
            set(target, "mediaSession", current)
            set(target, "webRtcManager", currentManager)
            set(target, "callSecurityState", securityState(sessionId, established = true))

            // Positive control: current manager + session + media session, established, native.
            // The cipher returns null, so the real decoder/playback is never entered.
            receive(target, currentManager, sessionId, current)
            assertEquals(1, cipher.opens.get())

            receive(target, staleManager, sessionId, current)
            receive(target, null, sessionId, current)
            receive(target, currentManager, sessionId, media(cipher))
            receive(target, currentManager, otherSessionId, current)
            set(target, "callSecurityState", securityState(otherSessionId, established = true))
            receive(target, currentManager, sessionId, current)
            set(target, "callSecurityState", securityState(sessionId, established = false))
            receive(target, currentManager, sessionId, current)
            set(target, "callSecurityState", securityState(sessionId, established = true))
            nativeAvailable = false
            receive(target, currentManager, sessionId, current)
            nativeAvailable = true
            assertEquals(1, cipher.opens.get())

            // Closed media session still installed: rejected before the cipher.
            current.close()
            receive(target, currentManager, sessionId, current)
            // Torn down: the old manager/session pair is stale.
            set(target, "mediaSession", null)
            set(target, "webRtcManager", null)
            set(target, "callSecurityState", null)
            receive(target, currentManager, sessionId, current)
            assertEquals(1, cipher.opens.get())
        }
    }
}

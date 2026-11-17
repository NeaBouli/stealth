package com.securecall.app.net

import com.securecall.app.crypto.MediaFrameCipher
import com.securecall.app.crypto.MediaSessionV3
import com.securecall.app.crypto.SessionKeyBinding
import com.securecall.app.ghostnet.media.playback.GhostAudioPlayer
import com.securecall.app.security.VerifiedCallAccept
import com.securecall.app.security.VerifiedCallInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SC-CALL-ACCEPT-ONCE: repeated, overlapping or stale local accepts must not rekey an
 * accepted call, replace its media session or stop its playback. Drives the real
 * `sendCallAccept` entry; synthetic identities only, no native library, keystore or network.
 * The JVM has no AndroidKeyStore identity, so a claimed accept ends on the existing
 * fail-closed `invalid_secure_invite` path; that is asserted as error-path ownership.
 */
class CallAcceptOnceTest {
    private val sessionId = "00000000-0000-4000-8000-0000000000a1"
    private val otherSessionId = "00000000-0000-4000-8000-0000000000a2"

    private class Harness(
        val service: WebSocketService,
        val lifecycle: AudioPlaybackLifecycle,
        val queue: MutableList<() -> Unit>,
        val client: HeartbeatClient,
        val errors: MutableList<String>,
    )

    private object NoopCipher : MediaFrameCipher {
        override fun seal(key: ByteArray, senderRole: Int, context: ByteArray, sequence: Long, plaintext: ByteArray) =
            ByteArray(MediaSessionV3.MIN_FRAME)
        override fun open(
            key: ByteArray, expectedPeerRole: Int, context: ByteArray,
            highest: Long, bitmap: Long, frame: ByteArray,
        ): ByteArray? = null
    }

    private fun set(target: WebSocketService, field: String, value: Any?) {
        WebSocketService::class.java.getDeclaredField(field).apply { isAccessible = true }.set(target, value)
    }

    private fun get(target: WebSocketService, field: String): Any? =
        WebSocketService::class.java.getDeclaredField(field).apply { isAccessible = true }.get(target)

    private fun harness(registered: Boolean): Harness {
        val target = Mockito.mock(WebSocketService::class.java, Answers.CALLS_REAL_METHODS)
        val lifecycle = AudioPlaybackLifecycle()
        val queue = mutableListOf<() -> Unit>()
        val client = Mockito.mock(HeartbeatClient::class.java)
        val errors = Collections.synchronizedList(mutableListOf<String>())
        set(target, "audioPlaybackLifecycle", lifecycle)
        set(target, "pendingCallQueue", queue)
        set(target, "client", client)
        set(target, "isRegistered", registered)
        set(target, "_onCallError", { code: String, _: String -> errors.add(code); Unit })
        return Harness(target, lifecycle, queue, client, errors)
    }

    private fun media(id: String): MediaSessionV3 {
        val keys = requireNotNull(SessionKeyBinding.deriveV3(ByteArray(32) { 5 }, "C".repeat(43), "D".repeat(43)))
        return MediaSessionV3(id, false, keys, NoopCipher)
    }

    private fun invite(id: String) = VerifiedCallInvite(
        id, "sc-caller", "sc-caller", "sc-callee", "sc-callee", "synthetic-ephemeral",
        "synthetic-identity", 2_000_000_000L, "synthetic-nonce", "synthetic-signature",
        "synthetic-digest",
    )

    /** Builds the service's private CallSecurityState through its real constructor. */
    private fun securityState(id: String, role: String = "CALLEE", accept: VerifiedCallAccept? = null): Any {
        val stateClass = Class.forName("com.securecall.app.net.CallSecurityState")
        val roleClass = Class.forName("com.securecall.app.net.SecureCallRole")
        val roleValue = roleClass.enumConstants.first { (it as Enum<*>).name == role }
        val constructor = stateClass.declaredConstructors.first {
            it.parameterCount == 10 && it.parameterTypes[0] == roleClass
        }.apply { isAccessible = true }
        return constructor.newInstance(
            roleValue, invite(id), "synthetic-transcript", accept, null, null,
            false, false, false, null,
        )
    }

    private fun claim(state: Any): AtomicBoolean =
        state.javaClass.getDeclaredField("acceptClaim").apply { isAccessible = true }.get(state) as AtomicBoolean

    private fun syntheticAccept(id: String) = VerifiedCallAccept(
        id, "synthetic-digest", "synthetic-accept-digest", "sc-callee", "sc-callee", "sc-caller",
        "synthetic-ephemeral", "synthetic-identity", 2_000_000_000L, "synthetic-nonce", "synthetic-signature",
    )

    /** Installs an accepted current call: claimed state, open media, live audio generation. */
    private fun acceptedCall(h: Harness, state: Any): Pair<MediaSessionV3, Any> {
        val current = media(sessionId)
        set(h.service, "callSecurityState", state)
        set(h.service, "mediaSession", current)
        return current to h.lifecycle.snapshot()
    }

    private fun assertUndisturbed(h: Harness, state: Any, current: MediaSessionV3, audio: Any) {
        assertSame(state, get(h.service, "callSecurityState"))
        assertSame(current, get(h.service, "mediaSession"))
        assertFalse("Accepted media session was closed", current.isClosed)
        assertTrue("Established playback was stopped", h.lifecycle.consume(audio) {})
        assertTrue(h.queue.isEmpty())
        assertTrue(h.errors.isEmpty())
        Mockito.verifyNoInteractions(h.client)
    }

    @Test
    fun repeatedAcceptForAcceptedCallNeitherRekeysNorStopsPlayback() {
        for (registered in listOf(true, false)) {
            val h = harness(registered)
            val state = securityState(sessionId)
            claim(state).set(true)
            val (current, audio) = acceptedCall(h, state)

            h.service.sendCallAccept(sessionId)
            h.service.sendCallAccept(sessionId)

            assertUndisturbed(h, state, current, audio)
            assertTrue(claim(state).get())
        }
    }

    @Test
    fun overlappingAcceptReturnsWithoutAudioStopWhileFirstOwnerRuns() {
        val h = harness(registered = true)
        val state = securityState(sessionId)
        set(h.service, "callSecurityState", state)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val player = Mockito.mock(GhostAudioPlayer::class.java)
        Mockito.doAnswer {
            entered.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            null
        }.`when`(player).retire()
        set(h.service, "audioPlayer", player)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit { h.service.sendCallAccept(sessionId) }
            assertTrue("First accept did not reach its audio stop", entered.await(5, TimeUnit.SECONDS))
            assertTrue(claim(state).get())
            val duringOwner = h.lifecycle.snapshot()

            h.service.sendCallAccept(sessionId)

            // The overlapping accept returned while the owner still holds the audio lock in its
            // stop; it neither waited for that lock nor replaced the audio generation.
            assertSame(duringOwner, h.lifecycle.snapshot())
            Mockito.verify(player, Mockito.times(1)).retire()
            assertTrue(h.errors.isEmpty())
            release.countDown()
            first.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
        Mockito.verify(player, Mockito.times(1)).retire()
        // The owner alone continues to the existing fail-closed local error (no keystore here).
        assertEquals(listOf("invalid_secure_invite"), h.errors)
        assertTrue(claim(state).get())
    }

    @Test
    fun distinctNewInviteIsIndependentlyAcceptable() {
        val h = harness(registered = true)
        val old = securityState(otherSessionId)
        claim(old).set(true)
        set(h.service, "callSecurityState", old)
        // The finished call's state is dropped (clearSession effect) and a distinct invite is
        // installed as a new CallSecurityState, which carries its own unclaimed accept claim.
        set(h.service, "callSecurityState", null)
        val fresh = securityState(sessionId)
        set(h.service, "callSecurityState", fresh)
        val ringing = h.lifecycle.snapshot()

        h.service.sendCallAccept(sessionId)

        assertTrue("New invite was treated as a duplicate", claim(fresh).get())
        assertFalse("Claim owner did not stop ringing", h.lifecycle.consume(ringing) {})
        assertEquals(listOf("invalid_secure_invite"), h.errors)
    }

    @Test
    fun queuedAcceptIsNotDiscardedByItsOwnReservationAndRunsOnce() {
        val h = harness(registered = false)
        val state = securityState(sessionId)
        set(h.service, "callSecurityState", state)

        h.service.sendCallAccept(sessionId)
        h.service.sendCallAccept(sessionId)

        assertEquals(2, h.queue.size)
        assertFalse("Queueing must not reserve the claim", claim(state).get())
        set(h.service, "isRegistered", true)
        val queued = h.queue.toList()
        h.queue.clear()
        queued.first().invoke()
        assertTrue("Queued accept was discarded", claim(state).get())
        val afterFirst = h.lifecycle.snapshot()
        queued.last().invoke()

        assertTrue("Second queued accept stopped audio", h.lifecycle.consume(afterFirst) {})
        assertEquals(listOf("invalid_secure_invite"), h.errors)
    }

    @Test
    fun staleAcceptDoesNotClaimOrDisturbAcceptedCurrentCall() {
        for (role in listOf("CALLEE", "CALLER")) {
            val h = harness(registered = true)
            val state = securityState(sessionId, role, accept = if (role == "CALLER") syntheticAccept(sessionId) else null)
            if (role == "CALLEE") claim(state).set(true)
            val (current, audio) = acceptedCall(h, state)

            h.service.sendCallAccept(otherSessionId)

            assertUndisturbed(h, state, current, audio)
        }
    }

    @Test
    fun invalidSessionKeepsFailClosedErrorWithoutClaimingCurrentState() {
        val h = harness(registered = true)
        val ringing = securityState(sessionId)
        set(h.service, "callSecurityState", ringing)

        h.service.sendCallAccept(otherSessionId)

        assertFalse("Invalid session claimed the current invite", claim(ringing).get())
        assertEquals(listOf("invalid_secure_invite"), h.errors)

        val none = harness(registered = true)
        none.service.sendCallAccept(sessionId)
        assertEquals(listOf("invalid_secure_invite"), none.errors)
    }

    @Test
    fun localFailureAfterClaimKeepsOwnershipWithoutRetryOrSecondError() {
        val h = harness(registered = true)
        val state = securityState(sessionId)
        set(h.service, "callSecurityState", state)

        h.service.sendCallAccept(sessionId)
        val afterFailure = h.lifecycle.snapshot()
        h.service.sendCallAccept(sessionId)

        assertTrue(claim(state).get())
        assertEquals(listOf("invalid_secure_invite"), h.errors)
        assertTrue(h.lifecycle.consume(afterFailure) {})
        assertTrue(h.queue.isEmpty())
        Mockito.verifyNoInteractions(h.client)
    }

    @Test
    fun unregisteredAcceptPausedBeforeAudioStopCannotStopAudioAfterRegisteredClaim() {
        val h = harness(registered = false)
        val state = securityState(sessionId)
        set(h.service, "callSecurityState", state)
        // Each audio stop records its thread and whether the invite was already claimed when it ran.
        val stops = Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
        val unregisteredAtStop = CountDownLatch(1)
        val release = CountDownLatch(1)
        Mockito.doAnswer { invocation ->
            val name = Thread.currentThread().name
            if (name == "unregistered-accept") {
                unregisteredAtStop.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
            }
            // Recorded at the actual side effect, after any pause.
            stops.add(name to claim(state).get())
            invocation.callRealMethod()
        }.`when`(h.service).killAllAudio()
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        fun worker(name: String) = Thread({
            try { h.service.sendCallAccept(sessionId) } catch (t: Throwable) { failures.add(t) }
        }, name)
        val unregistered = worker("unregistered-accept")
        val registered = worker("registered-accept")
        try {
            unregistered.start()
            assertTrue("Unregistered accept did not reach its audio stop", unregisteredAtStop.await(5, TimeUnit.SECONDS))
            assertFalse(claim(state).get())
            set(h.service, "isRegistered", true)
            registered.start()
            // Bounded wait: the registered accept either waits at the admission or (without
            // it) runs to completion, claiming and stopping audio before the paused stop.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (registered.state != Thread.State.BLOCKED && registered.state != Thread.State.TERMINATED &&
                System.nanoTime() < deadline) {
                Thread.yield()
            }
        } finally {
            release.countDown()
            unregistered.join(5_000)
            registered.join(5_000)
        }
        assertFalse(unregistered.isAlive || registered.isAlive)
        assertTrue(failures.toString(), failures.isEmpty())

        // The unregistered stop ran strictly before the legitimate claim; the claim owner stopped
        // ringing once afterwards. No unregistered audio stop observed a claimed invite.
        assertEquals(listOf("unregistered-accept" to false, "registered-accept" to true), stops.toList())
        assertTrue(claim(state).get())
        assertEquals(listOf("invalid_secure_invite"), h.errors)
        // The unregistered accept queued without reserving; its late callback is now a no-op.
        assertEquals(1, h.queue.size)
        val queued = h.queue.toList()
        h.queue.clear()
        queued.single().invoke()
        assertEquals(2, stops.size)
        assertEquals(listOf("invalid_secure_invite"), h.errors)
    }
}

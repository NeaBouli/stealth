package com.securecall.app.net

import com.securecall.app.crypto.MediaFrameCipher
import com.securecall.app.crypto.MediaSessionV3
import com.securecall.app.crypto.SessionKeyBinding
import com.securecall.app.security.VerifiedCallInvite
import com.securecall.crypto.CoreCrypto
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicInteger

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
        Mockito.mock(WebSocketService::class.java, Answers.CALLS_REAL_METHODS)

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

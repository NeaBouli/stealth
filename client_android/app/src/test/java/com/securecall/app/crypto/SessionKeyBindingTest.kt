package com.securecall.app.crypto

import com.securecall.app.security.IdentityProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SessionKeyBindingTest {
    private val sessionId = UUID.randomUUID().toString()
    private val identityA = "sc-${IdentityProtocol.encodeBase64Url(ByteArray(32) { 1 })}"
    private val identityB = "sc-${IdentityProtocol.encodeBase64Url(ByteArray(32) { 2 })}"
    private val ephemeralA = IdentityProtocol.encodeBase64Url(ByteArray(32) { 3 })
    private val ephemeralB = IdentityProtocol.encodeBase64Url(ByteArray(32) { 4 })
    private val nonceA = IdentityProtocol.encodeBase64Url(ByteArray(16) { 5 })
    private val nonceB = IdentityProtocol.encodeBase64Url(ByteArray(16) { 6 })
    private val invite = requireNotNull(IdentityProtocol.callInviteTranscript(
        sessionId, identityA, identityA, identityB, ephemeralA, 2_000_000_000, nonceA,
    ))
    private val inviteDigest = requireNotNull(IdentityProtocol.transcriptDigest(invite))
    private val accept = requireNotNull(IdentityProtocol.callAcceptTranscript(
        sessionId, inviteDigest, identityB, identityB, identityA,
        ephemeralB, 2_000_000_000, nonceB,
    ))
    private val acceptDigest = requireNotNull(IdentityProtocol.transcriptDigest(accept))

    private fun keys(base: ByteArray, invite: String = inviteDigest, accept: String = acceptDigest) =
        requireNotNull(SessionKeyBinding.deriveV3(base, invite, accept))

    @Test
    fun bothPeersDeriveSameContextBoundKeysAndCode() {
        val base = ByteArray(32) { it.toByte() }
        val caller = keys(base)
        val callee = keys(base.copyOf())
        assertArrayEquals(caller.callKey, callee.callKey)
        assertArrayEquals(caller.callerToCallee, callee.callerToCallee)
        assertArrayEquals(caller.calleeToCaller, callee.calleeToCaller)
        assertArrayEquals(caller.context, callee.context)
        assertEquals(32, caller.callKey.size)
        assertEquals(32, caller.context.size)
        assertEquals(
            SessionKeyBinding.securityCode(caller.callKey, sessionId, inviteDigest, acceptDigest),
            SessionKeyBinding.securityCode(callee.callKey, sessionId, inviteDigest, acceptDigest),
        )
        assertEquals(6, SessionKeyBinding.securityCode(caller.callKey, sessionId, inviteDigest, acceptDigest)?.length)
    }

    @Test
    fun directionAndCallKeysAreDistinctAndContextBound() {
        val k = keys(ByteArray(32) { 9 })
        assertFalse(k.callKey.contentEquals(k.callerToCallee))
        assertFalse(k.callKey.contentEquals(k.calleeToCaller))
        assertFalse(k.callerToCallee.contentEquals(k.calleeToCaller))
        val otherAccept = IdentityProtocol.transcriptDigest(accept + "x")!!
        val changed = keys(ByteArray(32) { 9 }, accept = otherAccept)
        assertFalse(changed.callerToCallee.contentEquals(k.callerToCallee))
        assertFalse(changed.context.contentEquals(k.context))
        assertNull(SessionKeyBinding.deriveV3(ByteArray(31), inviteDigest, acceptDigest))
        assertNull(SessionKeyBinding.deriveV3(ByteArray(32), "short", acceptDigest))
        k.clear()
        assertTrue(k.callKey.all { it == 0.toByte() })
        assertTrue(k.callerToCallee.all { it == 0.toByte() })
        assertTrue(k.calleeToCaller.all { it == 0.toByte() })
    }

    @Test
    fun transcriptAndRoleChangesCannotReuseConfirmation() {
        val key = keys(ByteArray(32) { 9 }).callKey
        val caller = SessionKeyBinding.confirmation(key, sessionId, "caller", inviteDigest, acceptDigest)
        val callee = SessionKeyBinding.confirmation(key, sessionId, "callee", inviteDigest, acceptDigest)
        assertNotEquals(caller, callee)
        assertTrue(SessionKeyBinding.matches(caller, caller))
        assertFalse(SessionKeyBinding.matches(caller, callee))

        val changedAccept = accept.replace(ephemeralB, IdentityProtocol.encodeBase64Url(ByteArray(32) { 8 }))
        val changedDigest = requireNotNull(IdentityProtocol.transcriptDigest(changedAccept))
        assertFalse(keys(ByteArray(32) { 9 }, accept = changedDigest).callKey.contentEquals(key))
    }

    @Test
    fun mediaGateAcceptsOnlyTheExpectedPeerConfirmation() {
        val key = keys(ByteArray(32) { 7 }).callKey
        val expected = SessionKeyBinding.confirmation(
            key, sessionId, "callee", inviteDigest, acceptDigest,
        )
        val valid = {
            SessionKeyBinding.acceptsPeerConfirmation(
                established = false,
                localConfirmationSent = true,
                protocol = SessionKeyBinding.CALL_PROTOCOL,
                expectedSessionId = sessionId,
                receivedSessionId = sessionId,
                expectedRole = "callee",
                receivedRole = "callee",
                expectedConfirmation = expected,
                receivedConfirmation = expected,
            )
        }
        assertTrue(valid())
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, true, 3, sessionId, sessionId, "callee", "callee", expected, "forged",
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, true, 3, sessionId, "wrong-session", "callee", "callee", expected, expected,
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, true, 3, sessionId, sessionId, "callee", "caller", expected, expected,
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, false, 3, sessionId, sessionId, "callee", "callee", expected, expected,
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            true, true, 3, sessionId, sessionId, "callee", "callee", expected, expected,
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, true, 1, sessionId, sessionId, "callee", "callee", expected, expected,
        ))
        // Protocol-2 confirmations are a downgrade and are rejected.
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, true, 2, sessionId, sessionId, "callee", "callee", expected, expected,
        ))
    }
}

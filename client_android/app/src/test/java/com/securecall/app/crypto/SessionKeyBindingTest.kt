package com.securecall.app.crypto

import com.securecall.app.security.IdentityProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun bothPeersDeriveSameContextBoundKeyAndCode() {
        val base = ByteArray(32) { it.toByte() }
        val caller = SessionKeyBinding.bind(base, invite, accept)
        val callee = SessionKeyBinding.bind(base.copyOf(), invite, accept)
        assertNotNull(caller)
        assertArrayEquals(caller, callee)
        assertEquals(32, caller!!.size)
        assertEquals(
            SessionKeyBinding.securityCode(caller, sessionId, inviteDigest, acceptDigest),
            SessionKeyBinding.securityCode(callee!!, sessionId, inviteDigest, acceptDigest),
        )
        assertEquals(6, SessionKeyBinding.securityCode(caller, sessionId, inviteDigest, acceptDigest)?.length)
    }

    @Test
    fun transcriptAndRoleChangesCannotReuseConfirmation() {
        val key = requireNotNull(SessionKeyBinding.bind(ByteArray(32) { 9 }, invite, accept))
        val caller = SessionKeyBinding.confirmation(key, sessionId, "caller", inviteDigest, acceptDigest)
        val callee = SessionKeyBinding.confirmation(key, sessionId, "callee", inviteDigest, acceptDigest)
        assertNotEquals(caller, callee)
        assertTrue(SessionKeyBinding.matches(caller, caller))
        assertFalse(SessionKeyBinding.matches(caller, callee))

        val changedAccept = accept.replace(ephemeralB, IdentityProtocol.encodeBase64Url(ByteArray(32) { 8 }))
        assertFalse(requireNotNull(SessionKeyBinding.bind(ByteArray(32) { 9 }, invite, changedAccept))
            .contentEquals(key))
    }

    @Test
    fun mediaGateAcceptsOnlyTheExpectedPeerConfirmation() {
        val key = requireNotNull(SessionKeyBinding.bind(ByteArray(32) { 7 }, invite, accept))
        val expected = SessionKeyBinding.confirmation(
            key, sessionId, "callee", inviteDigest, acceptDigest,
        )
        val valid = {
            SessionKeyBinding.acceptsPeerConfirmation(
                established = false,
                localConfirmationSent = true,
                protocol = 2,
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
            false, true, 2, sessionId, sessionId, "callee", "callee", expected, "forged",
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, true, 2, sessionId, "wrong-session", "callee", "callee", expected, expected,
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, true, 2, sessionId, sessionId, "callee", "caller", expected, expected,
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, false, 2, sessionId, sessionId, "callee", "callee", expected, expected,
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            true, true, 2, sessionId, sessionId, "callee", "callee", expected, expected,
        ))
        assertFalse(SessionKeyBinding.acceptsPeerConfirmation(
            false, true, 1, sessionId, sessionId, "callee", "callee", expected, expected,
        ))
    }
}

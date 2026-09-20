package com.securecall.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

class IdentityProtocolTest {
    private fun material(): Pair<java.security.KeyPair, CallIdentity> {
        val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val encoded = IdentityProtocol.encodeBase64Url(pair.public.encoded)
        return pair to requireNotNull(IdentityProtocol.identityFromPublicKey(encoded))
    }

    @Test
    fun directClientIdRecognizesCanonicalAndLegacyIdsOnly() {
        val (_, identity) = material()
        assertTrue(IdentityProtocol.isCanonicalIdentityId(identity.identityId))
        assertTrue(IdentityProtocol.isDirectClientId(identity.identityId))
        assertTrue(IdentityProtocol.isDirectClientId("android-legacy01"))
        assertFalse(IdentityProtocol.isDirectClientId("+302101234567"))
        assertFalse(IdentityProtocol.isDirectClientId("sc-not-a-key-hash"))
        assertFalse(IdentityProtocol.isDirectClientId(null))
    }

    private fun sign(pair: java.security.KeyPair, transcript: String): String {
        val bytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(transcript.toByteArray(Charsets.UTF_8))
            sign()
        }
        return IdentityProtocol.encodeBase64Url(bytes)
    }

    @Test
    fun identityIsSelfCertifyingAndRejectsNonP256() {
        val (_, identity) = material()
        assertEquals("sc-${identity.keyHash}", identity.identityId)
        assertEquals(43, identity.keyHash.length)
        assertNotNull(IdentityProtocol.identityFromPublicKey(identity.publicKey))

        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertNull(IdentityProtocol.identityFromPublicKey(IdentityProtocol.encodeBase64Url(rsa.public.encoded)))
    }

    @Test
    fun registrationTranscriptMatchesServerContract() {
        val challenge = "123e4567-e89b-42d3-a456-426614174000"
        val (_, identity) = material()
        val nonce = IdentityProtocol.encodeBase64Url(ByteArray(16) { it.toByte() })
        val transcript = IdentityProtocol.registrationTranscript(
            challenge, identity.identityId, identity.identityId,
            identity.keyHash, 2_000_000_300, nonce,
        )
        assertEquals(
            listOf("securecall-registration-v2", challenge, identity.identityId,
                identity.identityId, identity.keyHash, "2000000300", nonce).joinToString("\n"),
            transcript,
        )
        assertTrue(IdentityProtocol.validateRegistrationChallenge(
            challenge, requireNotNull(transcript), identity.identityId, identity,
            2_000_000_300, 2_000_000_000,
        ))
        assertFalse(IdentityProtocol.validateRegistrationChallenge(
            challenge, requireNotNull(transcript).replace(identity.identityId, "android-tampered"),
            identity.identityId, identity,
            2_000_000_300, 2_000_000_000,
        ))
    }

    @Test
    fun signedInviteAndAcceptRejectTamperingAndStaleMessages() {
        val (callerPair, caller) = material()
        val (calleePair, callee) = material()
        val now = 2_000_000_000L
        val sessionId = UUID.randomUUID().toString()
        val callerEphemeral = IdentityProtocol.encodeBase64Url(ByteArray(32) { (it + 1).toByte() })
        val nonce = IdentityProtocol.randomNonce()
        val inviteTranscript = requireNotNull(IdentityProtocol.callInviteTranscript(
            sessionId, caller.identityId, caller.identityId, callee.identityId,
            callerEphemeral, now, nonce,
        ))
        val invite = VerifiedCallInvite(
            sessionId, caller.identityId, caller.identityId, callee.identityId,
            callee.identityId, callerEphemeral, caller.publicKey, now, nonce,
            sign(callerPair, inviteTranscript), requireNotNull(IdentityProtocol.transcriptDigest(inviteTranscript)),
        )
        assertTrue(IdentityProtocol.verifyCallInvite(invite, now))
        assertFalse(IdentityProtocol.verifyCallInvite(invite.copy(ephemeralPublicKey =
            IdentityProtocol.encodeBase64Url(ByteArray(32) { 7 })), now))
        assertFalse(IdentityProtocol.verifyCallInvite(invite, now + 121))

        val calleeEphemeral = IdentityProtocol.encodeBase64Url(ByteArray(32) { (it + 2).toByte() })
        val acceptNonce = IdentityProtocol.randomNonce()
        val acceptTranscript = requireNotNull(IdentityProtocol.callAcceptTranscript(
            sessionId, invite.inviteDigest, callee.identityId, callee.identityId,
            caller.identityId, calleeEphemeral, now, acceptNonce,
        ))
        val accept = VerifiedCallAccept(
            sessionId, invite.inviteDigest, requireNotNull(IdentityProtocol.transcriptDigest(acceptTranscript)),
            callee.identityId, callee.identityId, caller.identityId, calleeEphemeral,
            callee.publicKey, now, acceptNonce, sign(calleePair, acceptTranscript),
        )
        assertTrue(IdentityProtocol.verifyCallAccept(accept, now))
        assertFalse(IdentityProtocol.verifyCallAccept(accept.copy(toIdentityId = callee.identityId), now))
    }
}

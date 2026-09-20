package com.securecall.app.security

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec

data class CallIdentity(val identityId: String, val keyHash: String, val publicKey: String)

data class VerifiedCallInvite(
    val sessionId: String,
    val fromClientId: String,
    val fromIdentityId: String,
    val to: String,
    val requestedTo: String,
    val ephemeralPublicKey: String,
    val identityPublicKey: String,
    val issuedAt: Long,
    val nonce: String,
    val signature: String,
    val inviteDigest: String,
    val callerPhone: String = "",
)

data class VerifiedCallAccept(
    val sessionId: String,
    val inviteDigest: String,
    val acceptDigest: String,
    val fromClientId: String,
    val fromIdentityId: String,
    val toIdentityId: String,
    val ephemeralPublicKey: String,
    val identityPublicKey: String,
    val issuedAt: Long,
    val nonce: String,
    val signature: String,
)

object IdentityProtocol {
    private val clientId = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val identityId = Regex("^sc-[A-Za-z0-9_-]{43}$")
    private val uuid = Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$")
    private val nonce = Regex("^[A-Za-z0-9_-]{22,64}$")
    private val digest = Regex("^[A-Za-z0-9_-]{43}$")
    private val signature = Regex("^[A-Za-z0-9_-]{64,144}$")

    @JvmStatic
    fun isCanonicalIdentityId(value: String?): Boolean =
        value != null && identityId.matches(value)

    @JvmStatic
    fun isDirectClientId(value: String?): Boolean =
        value != null && (value.startsWith("android-") || isCanonicalIdentityId(value))

    fun encodeBase64Url(value: ByteArray): String = value.toByteString().base64Url().trimEnd('=')

    fun decodeBase64Url(value: String, expectedSize: Int? = null): ByteArray? {
        if (value.isEmpty() || !Regex("^[A-Za-z0-9_-]+$").matches(value)) return null
        val bytes = value.decodeBase64()?.toByteArray() ?: return null
        if (expectedSize != null && bytes.size != expectedSize) return null
        return bytes.takeIf { encodeBase64Url(it) == value }
    }

    fun identityFromPublicKey(encoded: String): CallIdentity? {
        val bytes = decodeBase64Url(encoded) ?: return null
        if (bytes.size !in 80..160) return null
        return try {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes)) as? ECPublicKey
                ?: return null
            if (!sameParameters(key.params, p256Parameters()) || !key.encoded.contentEquals(bytes)) return null
            val hash = encodeBase64Url(MessageDigest.getInstance("SHA-256").digest(bytes))
            CallIdentity("sc-$hash", hash, encoded)
        } catch (_: Exception) {
            null
        }
    }

    fun registrationTranscript(
        challengeId: String,
        requestedClientId: String,
        identityIdValue: String,
        keyHash: String,
        expiresAt: Long,
        nonceValue: String,
    ): String? {
        if (!uuid.matches(challengeId) || !clientId.matches(requestedClientId)
            || !identityId.matches(identityIdValue) || !digest.matches(keyHash)
            || expiresAt <= 0 || !nonce.matches(nonceValue)) return null
        return listOf(
            "securecall-registration-v2", challengeId, requestedClientId,
            identityIdValue, keyHash, expiresAt.toString(), nonceValue,
        ).joinToString("\n")
    }

    fun validateRegistrationChallenge(
        challengeId: String,
        challenge: String,
        requestedClientId: String,
        identity: CallIdentity,
        expiresAt: Long,
        nowEpochSeconds: Long,
    ): Boolean {
        if (expiresAt <= nowEpochSeconds || expiresAt > nowEpochSeconds + 600) return false
        val lines = challenge.split('\n')
        if (lines.size != 7) return false
        val expected = registrationTranscript(
            challengeId, requestedClientId, identity.identityId,
            identity.keyHash, expiresAt, lines[6],
        ) ?: return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            challenge.toByteArray(Charsets.UTF_8),
        )
    }

    fun callInviteTranscript(
        sessionId: String,
        fromClientId: String,
        fromIdentityId: String,
        to: String,
        ephemeralPublicKey: String,
        issuedAt: Long,
        nonceValue: String,
    ): String? {
        if (!uuid.matches(sessionId) || !clientId.matches(fromClientId)
            || !identityId.matches(fromIdentityId) || !clientId.matches(to)
            || decodeBase64Url(ephemeralPublicKey, 32) == null || issuedAt <= 0
            || !nonce.matches(nonceValue)) return null
        return listOf(
            "securecall-call-invite-v2", sessionId, fromClientId, fromIdentityId,
            to, ephemeralPublicKey, issuedAt.toString(), nonceValue,
        ).joinToString("\n")
    }

    fun callAcceptTranscript(
        sessionId: String,
        inviteDigest: String,
        fromClientId: String,
        fromIdentityId: String,
        toIdentityId: String,
        ephemeralPublicKey: String,
        issuedAt: Long,
        nonceValue: String,
    ): String? {
        if (!uuid.matches(sessionId) || !digest.matches(inviteDigest)
            || !clientId.matches(fromClientId) || !identityId.matches(fromIdentityId)
            || !identityId.matches(toIdentityId) || decodeBase64Url(ephemeralPublicKey, 32) == null
            || issuedAt <= 0 || !nonce.matches(nonceValue)) return null
        return listOf(
            "securecall-call-accept-v2", sessionId, inviteDigest, fromClientId,
            fromIdentityId, toIdentityId, ephemeralPublicKey, issuedAt.toString(), nonceValue,
        ).joinToString("\n")
    }

    fun transcriptDigest(transcript: String): String? {
        if (!validTranscript(transcript)) return null
        return encodeBase64Url(MessageDigest.getInstance("SHA-256").digest(transcript.toByteArray(Charsets.UTF_8)))
    }

    fun verifyCallInvite(value: VerifiedCallInvite, nowEpochSeconds: Long): Boolean {
        if (value.fromClientId != value.fromIdentityId || value.to.isEmpty()
            || kotlin.math.abs(nowEpochSeconds - value.issuedAt) > 120) return false
        val identity = identityFromPublicKey(value.identityPublicKey) ?: return false
        if (identity.identityId != value.fromIdentityId) return false
        val transcript = callInviteTranscript(
            value.sessionId, value.fromClientId, value.fromIdentityId, value.requestedTo,
            value.ephemeralPublicKey, value.issuedAt, value.nonce,
        ) ?: return false
        return transcriptDigest(transcript) == value.inviteDigest
            && verify(identity.publicKey, transcript, value.signature)
    }

    fun verifyCallAccept(value: VerifiedCallAccept, nowEpochSeconds: Long): Boolean {
        if (value.fromClientId != value.fromIdentityId
            || kotlin.math.abs(nowEpochSeconds - value.issuedAt) > 120) return false
        val identity = identityFromPublicKey(value.identityPublicKey) ?: return false
        if (identity.identityId != value.fromIdentityId) return false
        val transcript = callAcceptTranscript(
            value.sessionId, value.inviteDigest, value.fromClientId, value.fromIdentityId,
            value.toIdentityId, value.ephemeralPublicKey, value.issuedAt, value.nonce,
        ) ?: return false
        return transcriptDigest(transcript) == value.acceptDigest
            && verify(identity.publicKey, transcript, value.signature)
    }

    fun randomNonce(): String = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
        .let(::encodeBase64Url)

    private fun verify(encodedPublicKey: String, transcript: String, encodedSignature: String): Boolean {
        if (!signature.matches(encodedSignature) || !validTranscript(transcript)) return false
        val key = publicKey(encodedPublicKey) ?: return false
        val bytes = decodeBase64Url(encodedSignature) ?: return false
        return try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(transcript.toByteArray(Charsets.UTF_8))
                verify(bytes)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun publicKey(encoded: String): PublicKey? {
        if (identityFromPublicKey(encoded) == null) return null
        return try {
            val bytes = decodeBase64Url(encoded) ?: return null
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
        } catch (_: Exception) {
            null
        }
    }

    private fun validTranscript(value: String): Boolean = value.length in 1..4096
        && value.startsWith("securecall-") && !value.contains('\u0000')

    private fun p256Parameters(): ECParameterSpec {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        return parameters.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun sameParameters(a: ECParameterSpec, b: ECParameterSpec): Boolean =
        a.curve == b.curve && a.generator == b.generator && a.order == b.order && a.cofactor == b.cofactor
}

package com.securecall.app.crypto

import com.securecall.app.security.IdentityProtocol
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * STX-24 call key schedule (call protocol 3). All keys are HKDF-SHA256 outputs of the
 * authenticated X25519 base key, salted with the signed invite/accept context.
 * [callKey] only feeds CALL_KEY_CONFIRM and the security code; media uses the two
 * direction keys. [clear] wipes every key together.
 */
class CallKeysV3 internal constructor(
    val callKey: ByteArray,
    val callerToCallee: ByteArray,
    val calleeToCaller: ByteArray,
    val context: ByteArray,
) {
    fun clear() {
        callKey.fill(0)
        callerToCallee.fill(0)
        calleeToCaller.fill(0)
    }
}

object SessionKeyBinding {
    const val CALL_PROTOCOL = 3
    private const val HMAC = "HmacSHA256"
    private val callKeyInfo = "SecureCall-Call-Key-v3".toByteArray(Charsets.US_ASCII)
    private val callerToCalleeInfo = "SecureCall-Media-Key-v3 caller->callee".toByteArray(Charsets.US_ASCII)
    private val calleeToCallerInfo = "SecureCall-Media-Key-v3 callee->caller".toByteArray(Charsets.US_ASCII)
    private val digest = Regex("^[A-Za-z0-9_-]{43}$")

    fun deriveV3(baseKey: ByteArray, inviteDigest: String, acceptDigest: String): CallKeysV3? {
        if (baseKey.size != 32) return null
        val context = contextDigest(inviteDigest, acceptDigest) ?: return null
        var pseudoRandomKey: ByteArray? = null
        return try {
            val prk = hmac(context, baseKey).also { pseudoRandomKey = it }
            CallKeysV3(
                callKey = hkdfExpand(prk, callKeyInfo, 32),
                callerToCallee = hkdfExpand(prk, callerToCalleeInfo, 32),
                calleeToCaller = hkdfExpand(prk, calleeToCallerInfo, 32),
                context = context,
            )
        } catch (_: Exception) {
            null
        } finally {
            pseudoRandomKey?.fill(0)
        }
    }

    fun confirmation(
        sessionKey: ByteArray,
        sessionId: String,
        role: String,
        inviteDigest: String,
        acceptDigest: String,
    ): String? {
        if (sessionKey.size != 32 || role !in setOf("caller", "callee")) return null
        val message = listOf(
            "securecall-key-confirm-v3", sessionId, role, inviteDigest, acceptDigest,
        ).joinToString("\n").toByteArray(Charsets.UTF_8)
        return try { IdentityProtocol.encodeBase64Url(hmac(sessionKey, message)) } catch (_: Exception) { null }
    }

    fun securityCode(
        sessionKey: ByteArray,
        sessionId: String,
        inviteDigest: String,
        acceptDigest: String,
    ): String? {
        if (sessionKey.size != 32) return null
        val message = listOf(
            "securecall-security-code-v3", sessionId, inviteDigest, acceptDigest,
        ).joinToString("\n").toByteArray(Charsets.UTF_8)
        return try {
            val bytes = hmac(sessionKey, message)
            val number = (((bytes[0].toLong() and 0xff) shl 24)
                or ((bytes[1].toLong() and 0xff) shl 16)
                or ((bytes[2].toLong() and 0xff) shl 8)
                or (bytes[3].toLong() and 0xff)) % 1_000_000L
            number.toString().padStart(6, '0')
        } catch (_: Exception) {
            null
        }
    }

    fun matches(expected: String?, received: String?): Boolean {
        if (expected == null || received == null) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            received.toByteArray(Charsets.US_ASCII),
        )
    }

    fun acceptsPeerConfirmation(
        established: Boolean,
        localConfirmationSent: Boolean,
        protocol: Int,
        expectedSessionId: String,
        receivedSessionId: String,
        expectedRole: String,
        receivedRole: String,
        expectedConfirmation: String?,
        receivedConfirmation: String?,
    ): Boolean = !established
        && localConfirmationSent
        && protocol == CALL_PROTOCOL
        && expectedSessionId == receivedSessionId
        && expectedRole in setOf("caller", "callee")
        && expectedRole == receivedRole
        && matches(expectedConfirmation, receivedConfirmation)

    /** 32-byte call context digest; also carried in every media frame header. */
    fun contextDigest(inviteDigest: String, acceptDigest: String): ByteArray? {
        if (!digest.matches(inviteDigest) || !digest.matches(acceptDigest)) return null
        val value = "securecall-session-context-v3\n$inviteDigest\n$acceptDigest"
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance(HMAC).run {
        init(SecretKeySpec(key, HMAC))
        doFinal(data)
    }

    private fun hkdfExpand(pseudoRandomKey: ByteArray, info: ByteArray, length: Int): ByteArray {
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val input = previous + info + byteArrayOf(counter.toByte())
            previous = hmac(pseudoRandomKey, input)
            val count = minOf(previous.size, length - offset)
            previous.copyInto(output, offset, 0, count)
            offset += count
            counter++
        }
        previous.fill(0)
        return output
    }
}

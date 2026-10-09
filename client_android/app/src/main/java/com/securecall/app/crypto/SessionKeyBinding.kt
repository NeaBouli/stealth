package com.securecall.app.crypto

import com.securecall.app.security.IdentityProtocol
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SessionKeyBinding {
    private const val HMAC = "HmacSHA256"
    private val aeadInfo = "SecureCall-AEAD-Key-v2".toByteArray(Charsets.US_ASCII)

    fun bind(baseKey: ByteArray, inviteTranscript: String, acceptTranscript: String): ByteArray? {
        if (baseKey.size != 32) return null
        val context = contextDigest(inviteTranscript, acceptTranscript) ?: return null
        return try {
            val pseudoRandomKey = hmac(context, baseKey)
            hkdfExpand(pseudoRandomKey, aeadInfo, 32)
        } catch (_: Exception) {
            null
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
            "securecall-key-confirm-v2", sessionId, role, inviteDigest, acceptDigest,
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
            "securecall-security-code-v2", sessionId, inviteDigest, acceptDigest,
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
        && protocol == 2
        && expectedSessionId == receivedSessionId
        && expectedRole in setOf("caller", "callee")
        && expectedRole == receivedRole
        && matches(expectedConfirmation, receivedConfirmation)

    private fun contextDigest(inviteTranscript: String, acceptTranscript: String): ByteArray? {
        val invite = IdentityProtocol.transcriptDigest(inviteTranscript) ?: return null
        val accept = IdentityProtocol.transcriptDigest(acceptTranscript) ?: return null
        val value = "securecall-session-context-v2\n$invite\n$accept"
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

package com.securecall.app.billing

import com.google.crypto.tink.subtle.Ed25519Verify
import okio.ByteString.Companion.decodeBase64

/** Dedicated gift proof. Never treats a commercial token or a raw tier as a gift. */
object TesterEntitlementVerifier {
    data class Verified(val grantHash: String, val expiresAtEpochSeconds: Long)

    private val keys = listOf("v", "iss", "aud", "sub", "pkg", "tier", "grant", "device", "iat", "exp")
    private val hash = Regex("[a-f0-9]{64}")
    private val identity = Regex("[A-Za-z0-9_.:-]{1,160}")
    private const val TTL = 30L * 86400
    private const val MAX_TIME = 9007199254740991L

    private fun decode(value: String): ByteArray {
        val bytes = value.decodeBase64() ?: error("Invalid tester entitlement")
        require(bytes.base64Url().trimEnd('=') == value) { "Invalid tester entitlement" }
        return bytes.toByteArray()
    }

    fun verify(token: String, publicKey: ByteArray, subject: String,
               deviceKeyHash: String, now: Long, applicationId: String): Verified {
        require(applicationId == "com.securecall.app.premium")
        require(token.length <= 4096 && publicKey.size == 32 && identity.matches(subject)
            && hash.matches(deviceKeyHash) && now > 0 && now <= MAX_TIME - TTL)
        val parts = token.split('.')
        require(parts.size == 3 && parts[0] == "sct1")
        val payload = decode(parts[1])
        val signature = decode(parts[2])
        require(signature.size == 64 && payload.all { it >= 0 })
        Ed25519Verify(publicKey).verify(signature, "sct1.${parts[1]}".toByteArray(Charsets.US_ASCII))
        val claims = linkedMapOf<String, String>()
        for (line in String(payload, Charsets.US_ASCII).split('\n')) {
            val i = line.indexOf('=')
            require(i > 0 && claims.put(line.substring(0, i), line.substring(i + 1)) == null)
        }
        require(claims.keys.toList() == keys)
        require(claims["v"] == "1" && claims["iss"] == "stealthx" && claims["aud"] == "securecall-tester")
        require(claims["pkg"] == "com.securecall.app.premium" && claims["tier"] == "PREMIUM")
        require(claims["sub"] == subject && claims["device"] == deviceKeyHash)
        val grant = claims.getValue("grant")
        require(hash.matches(grant))
        fun time(name: String): Long {
            val raw = claims.getValue(name)
            require(Regex("[1-9][0-9]*").matches(raw))
            return raw.toLong().also { require(it in 1..MAX_TIME) }
        }
        val issued = time("iat")
        val expires = time("exp")
        require(issued <= now + 300 && expires > now && expires > issued
            && expires - issued <= TTL && expires <= MAX_TIME - 7 * 86400)
        return Verified(grant, expires)
    }
}

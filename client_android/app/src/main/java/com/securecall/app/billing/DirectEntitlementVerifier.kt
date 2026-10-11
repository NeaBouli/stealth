package com.securecall.app.billing

import com.google.crypto.tink.subtle.Ed25519Verify
import okio.ByteString.Companion.decodeBase64
import java.security.GeneralSecurityException

/**
 * Verifies server-issued v2 direct-sale entitlement tokens against the strict
 * contract in backend/signaling/src/payments/entitlement_tokens.js.
 * Fails closed: any drift in signature, claims or timestamps is rejected with a
 * generic exception that never echoes token or claim content.
 */
object DirectEntitlementVerifier {

    data class VerifiedDirectEntitlement(
        val tier: String,
        val productId: String,
        val expiresAtEpochSeconds: Long
    )

    private const val MAX_TOKEN_LENGTH = 4096
    private const val PUBLIC_KEY_LENGTH = 32
    private const val SIGNATURE_LENGTH = 64
    private const val TOKEN_VERSION_V2 = "2"
    private const val ISSUER = "stealthx"
    private const val AUDIENCE = "securecall"
    private const val CATALOG_VERSION = "stealthx-lifetime-v1"
    private const val MAX_IAT_FUTURE_SKEW_SECONDS = 300L
    private const val MAX_LIFETIME_SECONDS = 30L * 24 * 60 * 60
    private const val MAX_SAFE_TIMESTAMP = 9007199254740991L

    private val ORDER_HASH_PATTERN = Regex("^[a-f0-9]{32}$")
    private val DIGITS_PATTERN = Regex("^[0-9]+$")

    private val EXPECTED_CLAIM_KEYS = setOf(
        "v", "iss", "aud", "sub", "tier", "product",
        "iat", "exp", "order", "catalog", "offer", "release"
    )

    private class ProductContract(val productId: String, val offerVersion: String)

    // Mirrors PRODUCTS in backend/signaling/src/payments/vlabs_fulfillment.js.
    private val PRODUCT_CONTRACTS = mapOf(
        "PRO" to ProductContract(
            "vlabs_securecall_pro_lifetime",
            "securecall-pro-eur-1500-lifetime-v1"
        ),
        "PREMIUM" to ProductContract(
            "vlabs_securecall_premium_lifetime",
            "securecall-premium-eur-2500-lifetime-v1"
        )
    )

    private fun invalidToken(): IllegalArgumentException =
        IllegalArgumentException("invalid entitlement token")

    private fun invalidSignature(): SecurityException =
        SecurityException("invalid entitlement signature")

    /** Decodes only canonical unpadded base64url; anything else is rejected. */
    private fun decodeCanonicalBase64Url(value: String): ByteArray? {
        val decoded = value.decodeBase64() ?: return null
        return if (decoded.base64Url().trimEnd('=') == value) decoded.toByteArray() else null
    }

    fun verify(
        token: String,
        publicKey: ByteArray,
        expectedSubject: String,
        expectedTier: String,
        expectedRelease: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000
    ): VerifiedDirectEntitlement {
        if (nowEpochSeconds <= 0 || nowEpochSeconds > MAX_SAFE_TIMESTAMP - MAX_IAT_FUTURE_SKEW_SECONDS ||
            expectedSubject.isEmpty() || expectedRelease.isEmpty()) throw invalidToken()
        if (token.length > MAX_TOKEN_LENGTH) throw invalidToken()
        val parts = token.split('.')
        if (parts.size != 2 || parts.any { it.isEmpty() }) throw invalidToken()
        val encodedPayload = parts[0]
        val encodedSignature = parts[1]

        val payloadBytes = decodeCanonicalBase64Url(encodedPayload) ?: throw invalidToken()
        val signature = decodeCanonicalBase64Url(encodedSignature) ?: throw invalidSignature()
        if (signature.size != SIGNATURE_LENGTH) throw invalidSignature()
        if (publicKey.size != PUBLIC_KEY_LENGTH) throw IllegalArgumentException("invalid verifier key")

        val verifier = try {
            Ed25519Verify(publicKey)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("invalid verifier key")
        }
        try {
            verifier.verify(signature, encodedPayload.toByteArray(Charsets.US_ASCII))
        } catch (e: GeneralSecurityException) {
            throw invalidSignature()
        }

        // Claims must be strict ASCII; no UTF-8 replacement tolerated.
        for (byte in payloadBytes) {
            if (byte < 0 || byte > 0x7F) throw invalidToken()
        }
        val payload = String(payloadBytes, Charsets.US_ASCII)
        if (payload.isEmpty()) throw invalidToken()

        val claims = LinkedHashMap<String, String>()
        for (line in payload.split('\n')) {
            val separator = line.indexOf('=')
            if (separator <= 0) throw invalidToken()
            if (claims.put(line.substring(0, separator), line.substring(separator + 1)) != null) {
                throw invalidToken()
            }
        }
        if (claims.keys != EXPECTED_CLAIM_KEYS) throw invalidToken()
        if (claims["v"] != TOKEN_VERSION_V2) throw invalidToken()
        if (claims["iss"] != ISSUER || claims["aud"] != AUDIENCE) throw invalidToken()

        val subject = claims["sub"] ?: throw invalidToken()
        if (subject.isEmpty() || subject != expectedSubject) throw invalidToken()

        val contract = PRODUCT_CONTRACTS[expectedTier] ?: throw invalidToken()
        if (claims["tier"] != expectedTier || claims["product"] != contract.productId) {
            throw invalidToken()
        }
        if (claims["catalog"] != CATALOG_VERSION || claims["offer"] != contract.offerVersion) {
            throw invalidToken()
        }
        if (claims["release"] != expectedRelease) throw invalidToken()
        if (!ORDER_HASH_PATTERN.matches(claims["order"] ?: "")) throw invalidToken()

        val issuedAt = parseTimestamp(claims["iat"])
        val expiresAt = parseTimestamp(claims["exp"])
        if (issuedAt > nowEpochSeconds + MAX_IAT_FUTURE_SKEW_SECONDS) throw invalidToken()
        if (expiresAt <= nowEpochSeconds) throw invalidToken()
        if (expiresAt <= issuedAt || expiresAt - issuedAt > MAX_LIFETIME_SECONDS) throw invalidToken()

        return VerifiedDirectEntitlement(expectedTier, contract.productId, expiresAt)
    }

    private fun parseTimestamp(value: String?): Long {
        if (value == null || !DIGITS_PATTERN.matches(value)) throw invalidToken()
        val parsed = value.toLongOrNull() ?: throw invalidToken()
        if (parsed <= 0 || parsed > MAX_SAFE_TIMESTAMP) throw invalidToken()
        return parsed
    }
}

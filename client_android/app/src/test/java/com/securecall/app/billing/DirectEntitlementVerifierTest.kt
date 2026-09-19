package com.securecall.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

class DirectEntitlementVerifierTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val otherKeyPair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    // Raw 32-byte Ed25519 public key (last 32 bytes of the X.509 encoding).
    private val rawPublicKey: ByteArray = keyPair.public.encoded.takeLast(32).toByteArray()
    private val rawOtherPublicKey: ByteArray = otherKeyPair.public.encoded.takeLast(32).toByteArray()

    private fun sign(payload: String, key: PrivateKey): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(key)
        signer.update(payload.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())
    }

    private fun issueToken(lines: List<String>, key: PrivateKey = keyPair.private): String {
        val encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(lines.joinToString("\n").toByteArray(StandardCharsets.US_ASCII))
        return "$encodedPayload.${sign(encodedPayload, key)}"
    }

    private fun issueRawPayloadToken(payload: ByteArray, key: PrivateKey = keyPair.private): String {
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        return "$encodedPayload.${sign(encodedPayload, key)}"
    }

    private fun validClaims(
        version: String = "2",
        subject: String = SUBJECT,
        tier: String = "PRO",
        product: String = PRO_PRODUCT,
        audience: String = "securecall",
        issuedAt: Long = NOW - 60,
        expiresAt: Long = NOW + 3600,
        catalog: String = CATALOG,
        offer: String = PRO_OFFER,
        release: String = RELEASE
    ): List<String> = listOf(
        "v=$version",
        "iss=stealthx",
        "aud=$audience",
        "sub=$subject",
        "tier=$tier",
        "product=$product",
        "iat=$issuedAt",
        "exp=$expiresAt",
        "order=${"0123456789abcdef".repeat(2)}",
        "catalog=$catalog",
        "offer=$offer",
        "release=$release"
    )

    private fun verifyPro(
        token: String,
        publicKey: ByteArray = rawPublicKey,
        subject: String = SUBJECT,
        tier: String = "PRO",
        release: String = RELEASE
    ) = DirectEntitlementVerifier.verify(token, publicKey, subject, tier, release, NOW)

    @Test
    fun validProContractVerifies() {
        val token = issueToken(validClaims())
        val entitlement = verifyPro(token)
        assertEquals("PRO", entitlement.tier)
        assertEquals(PRO_PRODUCT, entitlement.productId)
        assertEquals(NOW + 3600, entitlement.expiresAtEpochSeconds)
    }

    @Test
    fun validPremiumContractVerifies() {
        val token = issueToken(
            validClaims(tier = "PREMIUM", product = PREMIUM_PRODUCT, offer = PREMIUM_OFFER)
        )
        val entitlement = DirectEntitlementVerifier.verify(
            token, rawPublicKey, SUBJECT, "PREMIUM", RELEASE, NOW
        )
        assertEquals("PREMIUM", entitlement.tier)
        assertEquals(PREMIUM_PRODUCT, entitlement.productId)
    }

    @Test
    fun tamperedPayloadRejected() {
        val token = issueToken(validClaims())
        val payload = token.substringBefore('.')
        // Swap two characters: still canonical base64url, but the signature no longer matches.
        val tampered = payload[1].toString() + payload[0] + payload.substring(2)
        assertThrows(SecurityException::class.java) {
            verifyPro("$tampered.${token.substringAfter('.')}")
        }
    }

    @Test
    fun wrongKeyRejected() {
        val token = issueToken(validClaims())
        assertThrows(SecurityException::class.java) { verifyPro(token, publicKey = rawOtherPublicKey) }
    }

    @Test
    fun wrongSubjectRejected() {
        val token = issueToken(validClaims())
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token, subject = "other-device") }
    }

    @Test
    fun emptySubjectRejected() {
        val token = issueToken(validClaims(subject = ""))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token, subject = "") }
    }

    @Test
    fun wrongTierRejected() {
        val token = issueToken(validClaims())
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token, tier = "PREMIUM") }
    }

    @Test
    fun wrongProductRejected() {
        val token = issueToken(validClaims(product = PREMIUM_PRODUCT))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun wrongCatalogRejected() {
        val token = issueToken(validClaims(catalog = "stealthx-lifetime-v2"))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun wrongOfferRejected() {
        val token = issueToken(validClaims(offer = PREMIUM_OFFER))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun wrongReleaseRejected() {
        val token = issueToken(validClaims())
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token, release = "securecall-android-9.9.9-vc1-api36") }
    }

    @Test
    fun wrongAudienceRejected() {
        val token = issueToken(validClaims(audience = "securechat"))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun expiredTokenRejected() {
        val token = issueToken(validClaims(issuedAt = NOW - 7200, expiresAt = NOW - 3600))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
        val boundaryToken = issueToken(validClaims(issuedAt = NOW - 3600, expiresAt = NOW))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(boundaryToken) }
    }

    @Test
    fun futureIssuedAtBeyondSkewRejected() {
        val token = issueToken(validClaims(issuedAt = NOW + 301, expiresAt = NOW + 3601))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun futureIssuedAtWithinSkewAccepted() {
        val token = issueToken(validClaims(issuedAt = NOW + 300, expiresAt = NOW + 3900))
        verifyPro(token)
    }

    @Test
    fun excessiveLifespanRejected() {
        val token = issueToken(validClaims(issuedAt = NOW - 60, expiresAt = NOW - 60 + MAX_LIFETIME + 1))
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun duplicateClaimRejected() {
        val token = issueToken(validClaims() + "sub=$SUBJECT")
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun missingClaimRejected() {
        val token = issueToken(validClaims().filterNot { it.startsWith("order=") })
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun extraClaimRejected() {
        val token = issueToken(validClaims() + "extra=1")
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun v1TokenRejected() {
        val v1Claims = listOf(
            "v=1", "iss=stealthx", "aud=securecall", "sub=$SUBJECT", "tier=PRO",
            "product=$PRO_PRODUCT", "iat=${NOW - 60}", "exp=${NOW + 3600}",
            "order=${"0123456789abcdef".repeat(2)}"
        )
        assertThrows(IllegalArgumentException::class.java) { verifyPro(issueToken(v1Claims)) }
        // Even a v1 flag on an otherwise complete v2 shape is rejected.
        assertThrows(IllegalArgumentException::class.java) {
            verifyPro(issueToken(validClaims(version = "1")))
        }
    }

    @Test
    fun malformedBase64Rejected() {
        assertThrows(IllegalArgumentException::class.java) { verifyPro("!!!.${"a".repeat(86)}") }
        // Standard-alphabet characters are not canonical base64url.
        assertThrows(IllegalArgumentException::class.java) { verifyPro("+w.${"a".repeat(86)}") }
        // Padded input is not canonical either.
        assertThrows(IllegalArgumentException::class.java) {
            verifyPro(issueToken(validClaims()).let { "${it.substringBefore('.')}==.${it.substringAfter('.')}" })
        }
    }

    @Test
    fun wrongSignatureLengthRejected() {
        val token = issueToken(validClaims())
        val shortSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(63))
        assertThrows(SecurityException::class.java) {
            verifyPro("${token.substringBefore('.')}.$shortSignature")
        }
    }

    @Test
    fun malformedTokenShapeRejected() {
        assertThrows(IllegalArgumentException::class.java) { verifyPro("a.b.c") }
        assertThrows(IllegalArgumentException::class.java) { verifyPro("noparts") }
        assertThrows(IllegalArgumentException::class.java) { verifyPro("a".repeat(4097)) }
    }

    @Test
    fun nonAsciiClaimsRejected() {
        val payload = validClaims().joinToString("\n").toByteArray(StandardCharsets.US_ASCII) + byteArrayOf(0xC3.toByte())
        val token = issueRawPayloadToken(payload)
        assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
    }

    @Test
    fun errorsDoNotLeakTokenContent() {
        val token = issueToken(validClaims(audience = "securechat"))
        val error = assertThrows(IllegalArgumentException::class.java) { verifyPro(token) }
        assertEquals("invalid entitlement token", error.message)
        assertTrue(!token.contains(error.message ?: "\u0000"))
        assertTrue(!error.message!!.contains(SUBJECT))

        val signed = issueToken(validClaims())
        val invalidSignature = assertThrows(SecurityException::class.java) {
            verifyPro(signed, publicKey = rawOtherPublicKey)
        }
        assertEquals("invalid entitlement signature", invalidSignature.message)

        val invalidKey = assertThrows(IllegalArgumentException::class.java) {
            verifyPro(signed, publicKey = ByteArray(31))
        }
        assertEquals("invalid verifier key", invalidKey.message)
    }

    private companion object {
        const val NOW = 1_800_000_000L
        const val SUBJECT = "device-test-001"
        const val RELEASE = "securecall-android-1.0.50-vc78017-api36"
        const val CATALOG = "stealthx-lifetime-v1"
        const val PRO_PRODUCT = "vlabs_securecall_pro_lifetime"
        const val PREMIUM_PRODUCT = "vlabs_securecall_premium_lifetime"
        const val PRO_OFFER = "securecall-pro-eur-1500-lifetime-v1"
        const val PREMIUM_OFFER = "securecall-premium-eur-2500-lifetime-v1"
        const val MAX_LIFETIME = 30L * 24 * 60 * 60
    }
}

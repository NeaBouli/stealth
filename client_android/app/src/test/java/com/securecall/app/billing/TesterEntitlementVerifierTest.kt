package com.securecall.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.io.File

class TesterEntitlementVerifierTest {
    private val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val key = pair.public.encoded.takeLast(32).toByteArray()
    private val device = "a".repeat(64)
    private val now = 1700000000L
    private fun token(transform: (String) -> String = { it }): String {
        val text = "v=1\niss=stealthx\naud=securecall-tester\nsub=synthetic-A\npkg=com.securecall.app.premium\ntier=PREMIUM\ngrant=${"b".repeat(64)}\ndevice=$device\niat=$now\nexp=${now + 30 * 86400}"
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(transform(text).toByteArray())
        val signed = "sct1.$payload"
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(pair.private)
        signer.update(signed.toByteArray())
        return "$signed.${Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())}"
    }
    @Test fun validProofAndRepeat() {
        val proof = token()
        repeat(2) { assertEquals("b".repeat(64), TesterEntitlementVerifier.verify(proof, key, "synthetic-A", device, now, "com.securecall.app.premium").grantHash) }
    }
    @Test fun actualNodeIssuerIsCompatible() {
        val repo = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "backend/signaling/src/payments/tester_entitlement_tokens.js").isFile }
        val script = """
            const c=require('crypto');
            const {issueTesterEntitlement: issue}=require('./backend/signaling/src/payments/tester_entitlement_tokens');
            const p=c.generateKeyPairSync('ed25519');
            const token=issue({subject:'synthetic-A', grantHash:'b'.repeat(64),deviceKeyHash:'a'.repeat(64),
              privateKey:p.privateKey.export({type:'pkcs8',format:'pem'}),nowSeconds:1700000000});
            console.log(p.publicKey.export({type:'spki',format:'der'}).subarray(-32).toString('base64url'));
            console.log(token);
        """.trimIndent()
        val process = ProcessBuilder("node", "-e", script).directory(repo).start()
        val lines = process.inputStream.bufferedReader().readLines()
        assertEquals(0, process.waitFor())
        assertEquals(2, lines.size)
        val result = TesterEntitlementVerifier.verify(lines[1], Base64.getUrlDecoder().decode(lines[0]), "synthetic-A", device, now, "com.securecall.app.premium")
        assertEquals("b".repeat(64), result.grantHash)
        assertThrows(Exception::class.java) {
            DirectEntitlementVerifier.verify(lines[1], Base64.getUrlDecoder().decode(lines[0]), "synthetic-A", "PREMIUM", "fixture-release", now)
        }
    }
    @Test fun wrongDeviceSubjectAndExpiryFail() {
        val proof = token()
        assertThrows(Exception::class.java) { TesterEntitlementVerifier.verify(proof, key, "synthetic-A", "c".repeat(64), now, "com.securecall.app.premium") }
        assertThrows(Exception::class.java) { TesterEntitlementVerifier.verify(proof, key, "synthetic-B", device, now, "com.securecall.app.premium") }
        assertThrows(Exception::class.java) { TesterEntitlementVerifier.verify(proof, key, "synthetic-A", device, now + 30 * 86400, "com.securecall.app.premium") }
    }
    @Test fun signedInvalidClaimsFail() {
        val transforms: List<(String) -> String> = listOf(
            { it.replace("com.securecall.app.premium", "com.securecall.app.free") },
            { it.replace("securecall-tester", "securecall") },
            { it + "\nv=1" }, { it + "\nunknown=1" },
            { it.replace("iat=$now", "iat=1e9") },
            { it.replace("exp=${now + 30 * 86400}", "exp=${now + 30 * 86400 + 1}") }
        )
        for (change in transforms) {
            assertThrows(Exception::class.java) { TesterEntitlementVerifier.verify(token(change), key, "synthetic-A", device, now, "com.securecall.app.premium") }
        }
    }
    @Test fun badSignatureAndDomainFail() {
        assertThrows(Exception::class.java) { TesterEntitlementVerifier.verify(token() + "=", key, "synthetic-A", device, now, "com.securecall.app.premium") }
        assertThrows(Exception::class.java) { TesterEntitlementVerifier.verify(token().replace("sct1.", "sct2."), key, "synthetic-A", device, now, "com.securecall.app.premium") }
        assertThrows(Exception::class.java) { TesterEntitlementVerifier.verify(token(), ByteArray(32), "synthetic-A", device, now, "com.securecall.app.premium") }
    }
}

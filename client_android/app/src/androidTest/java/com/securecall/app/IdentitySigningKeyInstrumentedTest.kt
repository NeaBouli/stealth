package com.securecall.app

import android.security.keystore.KeyInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.securecall.app.security.IdentityProtocol
import com.securecall.app.security.IdentitySigningKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IdentitySigningKeyInstrumentedTest {
    @Test
    fun identityKeyIsStableNonExportableAndSignsOnlyCanonicalProtocolTranscript() {
        val first = IdentitySigningKey.ensureIdentity()
        if (first == null) fail(identityFailureSummary())
        assertEquals(first, IdentitySigningKey.existingIdentity())

        val transcript = IdentityProtocol.registrationTranscript(
            UUID.randomUUID().toString(),
            first!!.identityId,
            first.identityId,
            first.keyHash,
            System.currentTimeMillis() / 1000 + 300,
            IdentityProtocol.randomNonce(),
        )
        assertNotNull(transcript)
        assertNotNull(IdentitySigningKey.sign(transcript!!))
        assertNull(IdentitySigningKey.sign("not-a-securecall-transcript"))

        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(IdentitySigningKey.ALIAS, null)
        assertNotNull(privateKey)
        assertNull("AndroidKeyStore private material must not be exportable", privateKey.encoded)
        assertTrue(store.containsAlias(IdentitySigningKey.ALIAS))
    }

    private fun identityFailureSummary(): String = runCatching {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliasPresent = store.containsAlias(IdentitySigningKey.ALIAS)
        val key = store.getKey(IdentitySigningKey.ALIAS, null) as? PrivateKey
        val publicKey = store.getCertificate(IdentitySigningKey.ALIAS)?.publicKey as? ECPublicKey
        val signVerify = if (key != null && publicKey != null) runCatching {
            val probe = "securecall-identity-test-diagnostic".toByteArray(Charsets.UTF_8)
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(key)
                update(probe)
                sign()
            }
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(probe)
                verify(signature)
            }
        }.getOrDefault(false) else false
        val keyInfo = if (key == null) "missing" else runCatching {
            KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                .getKeySpec(key, KeyInfo::class.java)
        }.fold(
            onSuccess = {
                val digests = runCatching { it.digests.joinToString(",") }.getOrDefault("unavailable")
                "origin=${it.origin},size=${it.keySize},purposes=${it.purposes},digests=$digests"
            },
            onFailure = { "unavailable:${it.javaClass.simpleName}" },
        )
        "Identity key rejected: alias=$aliasPresent,key=${key?.algorithm ?: "missing"}," +
            "nonExportable=${key?.encoded == null},curveBits=${publicKey?.params?.curve?.field?.fieldSize}," +
            "signVerify=$signVerify,keyInfo=$keyInfo"
    }.getOrElse { "Identity key diagnostic failed: ${it.javaClass.simpleName}" }
}

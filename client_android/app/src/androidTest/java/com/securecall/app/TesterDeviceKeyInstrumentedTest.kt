package com.securecall.app

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.securecall.app.billing.TesterDeviceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

@RunWith(AndroidJUnit4::class)
class TesterDeviceKeyInstrumentedTest {
    @Test
    fun hardwareKeyIsCreatedReusedAndCanProvePossession() {
        val hardwareBackedSigningAvailable = probeHardwareBackedSigning()
        val firstOrNull = TesterDeviceKey.ensureHardwareKeyIdentity()
        val secondOrNull = TesterDeviceKey.ensureHardwareKeyIdentity()

        if (!hardwareBackedSigningAvailable) {
            assertNull(firstOrNull)
            assertNull(secondOrNull)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertFalse(store.containsAlias(TesterDeviceKey.ALIAS))
            return
        }

        assertNotNull(firstOrNull)
        assertNotNull(secondOrNull)
        val first = firstOrNull!!
        val second = secondOrNull!!
        assertEquals(first, second)
        assertTrue(first.keyHash.matches(Regex("[a-f0-9]{64}")))

        val publicKeyBytes = Base64.decode(
            first.publicKey,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        assertEquals(
            first.publicKey,
            Base64.encodeToString(
                publicKeyBytes,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        )
        assertEquals(
            first.keyHash,
            MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        )
        val challenge = "securecall-tester-device-instrumentation-v1"
        val encodedSignatureOrNull = TesterDeviceKey.signChallenge(challenge)
        assertNotNull(encodedSignatureOrNull)
        val encodedSignature = encodedSignatureOrNull!!
        val signature = Base64.decode(
            encodedSignature,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))
        assertTrue(Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(challenge.toByteArray(Charsets.UTF_8))
            verify(signature)
        })
    }

    private fun probeHardwareBackedSigning(): Boolean {
        val alias = "securecall_tester_hardware_probe_v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return try {
            if (store.containsAlias(alias)) store.deleteEntry(alias)
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
                generateKeyPair()
            }
            val privateKey = store.getKey(alias, null) as PrivateKey
            val info = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                .getKeySpec(privateKey, KeyInfo::class.java)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= 31) {
                info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                info.isInsideSecureHardware
            }
        } finally {
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }
}

package com.securecall.app.billing

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

data class TesterDeviceIdentity(val keyHash: String, val publicKey: String)

/** Hardware-backed signing key used only by the Direct Premium tester-license flow. */
object TesterDeviceKey {
    const val ALIAS = "securecall_tester_enrolled_v1"

    @Synchronized
    fun ensureHardwareKeyIdentity(): TesterDeviceIdentity? {
        return try {
            val store = loadStore()
            if (store.containsAlias(ALIAS)) {
                hardwareIdentity(store)
            } else {
                try {
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                        initialize(
                            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                                .setDigests(KeyProperties.DIGEST_SHA256)
                                .setUserAuthenticationRequired(false)
                                .build()
                        )
                        generateKeyPair()
                    }
                    hardwareIdentity(store).also { identity ->
                        if (identity == null) store.deleteEntry(ALIAS)
                    }
                } catch (_: Exception) {
                    // The alias did not exist before this call. Remove a partially-created key,
                    // but never replace or delete an alias that predated activation.
                    runCatching { if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS) }
                    null
                }
            }
        } catch (_: Exception) { null }
    }

    fun existingHardwareKeyIdentity(): TesterDeviceIdentity? = try {
        hardwareIdentity(loadStore())
    } catch (_: Exception) { null }

    fun signChallenge(challenge: String): String? = try {
        if (challenge.length !in 1..512 || existingHardwareKeyIdentity() == null) null else {
            val store = loadStore()
            val key = store.getKey(ALIAS, null) as PrivateKey
            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initSign(key)
                update(challenge.toByteArray(Charsets.UTF_8))
            }.sign()
            Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    } catch (_: Exception) { null }

    fun existingHardwareKeyHash(): String? = existingHardwareKeyIdentity()?.keyHash

    private fun loadStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun hardwareIdentity(store: KeyStore): TesterDeviceIdentity? = try {
        val privateKey = store.getKey(ALIAS, null) as? PrivateKey
        val certificate = store.getCertificate(ALIAS)
        if (privateKey == null || certificate == null) null else {
            val info = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                .getKeySpec(privateKey, KeyInfo::class.java)
            @Suppress("DEPRECATION")
            val hardware = if (Build.VERSION.SDK_INT >= 31) {
                info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else info.isInsideSecureHardware
            val encoded = certificate.publicKey.encoded
            if (!hardware || info.origin != KeyProperties.ORIGIN_GENERATED ||
                info.keySize != 256 || info.purposes and KeyProperties.PURPOSE_SIGN == 0 ||
                !info.digests.contains(KeyProperties.DIGEST_SHA256) || encoded.isEmpty()) {
                null
            } else {
                val hash = MessageDigest.getInstance("SHA-256").digest(encoded)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val publicKey = Base64.encodeToString(
                    encoded,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
                TesterDeviceIdentity(hash, publicKey)
            }
        }
    } catch (_: Exception) { null }
}

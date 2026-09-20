package com.securecall.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Per-install, non-exportable identity used to authenticate signaling. */
object IdentitySigningKey {
    const val ALIAS = "securecall_signaling_identity_v2"

    @Synchronized
    fun ensureIdentity(): CallIdentity? {
        return try {
            val store = loadStore()
            if (!store.containsAlias(ALIAS)) {
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
            }
            validatedIdentity(store)
        } catch (_: Exception) {
            null
        }
    }

    fun existingIdentity(): CallIdentity? = try { validatedIdentity(loadStore()) } catch (_: Exception) { null }

    fun sign(transcript: String): String? {
        if (transcript.length !in 1..4096 || !transcript.startsWith("securecall-")
            || transcript.contains('\u0000') || existingIdentity() == null) return null
        return try {
            val key = loadStore().getKey(ALIAS, null) as? PrivateKey ?: return null
            val bytes = Signature.getInstance("SHA256withECDSA").run {
                initSign(key)
                update(transcript.toByteArray(Charsets.UTF_8))
                sign()
            }
            IdentityProtocol.encodeBase64Url(bytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun validatedIdentity(store: KeyStore): CallIdentity? {
        val key = store.getKey(ALIAS, null) as? PrivateKey ?: return null
        val certificate = store.getCertificate(ALIAS) ?: return null
        val info = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java)
        if (info.origin != KeyProperties.ORIGIN_GENERATED || info.keySize != 256
            || info.purposes and KeyProperties.PURPOSE_SIGN == 0
            || !info.digests.contains(KeyProperties.DIGEST_SHA256)) return null
        return IdentityProtocol.identityFromPublicKey(
            IdentityProtocol.encodeBase64Url(certificate.publicKey.encoded)
        )
    }
}

package com.securecall.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec

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
        val publicKey = certificate.publicKey as? ECPublicKey ?: return null
        if (!key.algorithm.equals(KeyProperties.KEY_ALGORITHM_EC, ignoreCase = true)
            || key.encoded != null
            || !isP256(publicKey)
            || !canSignAndVerify(key, publicKey)) return null

        // API 24 software Keystore implementations may throw while exposing KeyInfo even though
        // the key is non-exportable and usable. Enforce every property the provider does expose;
        // the curve and signing probe above remain mandatory on every supported API level.
        readKeyInfo(key)?.let { info ->
            if (info.origin != KeyProperties.ORIGIN_GENERATED
                || info.purposes and KeyProperties.PURPOSE_SIGN == 0
                || (info.keySize > 0 && info.keySize != 256)) return null
            val digests = runCatching { info.digests }.getOrNull()
            if (!digests.isNullOrEmpty() && !digests.contains(KeyProperties.DIGEST_SHA256)) return null
        }
        return IdentityProtocol.identityFromPublicKey(
            IdentityProtocol.encodeBase64Url(publicKey.encoded)
        )
    }

    private fun readKeyInfo(key: PrivateKey): KeyInfo? = runCatching {
        KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java)
    }.getOrNull()

    private fun isP256(publicKey: ECPublicKey): Boolean = runCatching {
        val expected = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
        publicKey.params.curve == expected.curve
            && publicKey.params.generator == expected.generator
            && publicKey.params.order == expected.order
            && publicKey.params.cofactor == expected.cofactor
    }.getOrDefault(false)

    private fun canSignAndVerify(key: PrivateKey, publicKey: ECPublicKey): Boolean = runCatching {
        val probe = "securecall-identity-key-validation-v1".toByteArray(Charsets.UTF_8)
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
    }.getOrDefault(false)
}

package com.securecall.app.billing

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import android.util.Base64

/** Reads existing enrolled key only. Missing/lost/software keys never recreate access. */
object TesterDeviceKey {
    const val ALIAS = "securecall_tester_enrolled_v1"

    fun signChallenge(challenge: String): String? = try {
        if (challenge.length !in 1..512 || existingHardwareKeyHash() == null) null else {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = store.getKey(ALIAS, null) as PrivateKey
            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initSign(key)
                update(challenge.toByteArray(Charsets.UTF_8))
            }.sign()
            Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    } catch (_: Exception) { null }

    fun existingHardwareKeyHash(): String? = try {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
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
            if (!hardware || info.origin != KeyProperties.ORIGIN_GENERATED) null
            else MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    } catch (_: Exception) { null }
}

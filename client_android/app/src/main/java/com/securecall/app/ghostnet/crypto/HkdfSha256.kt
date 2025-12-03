package com.securecall.app.ghostnet.crypto

import android.util.Log
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * CRYPTO-05:
 * HKDF-SHA256 Utility.
 *
 * Wird genutzt, um aus sharedSecret -> masterKey, sendKey, recvKey abzuleiten.
 * Aktuell reine JVM/Android-Implementierung, später optional ersetzbar
 * durch Rust/JNI, falls notwendig.
 */
object HkdfSha256 {

    private const val TAG = "HKDF_SHA256"
    private const val HMAC_ALG = "HmacSHA256"

    fun deriveKeys(sharedSecret: ByteArray, info: String, outLen: Int): ByteArray {
        Log.d(TAG, "deriveKeys(): secretLen=${sharedSecret.size}, info=$info, outLen=$outLen")

        val salt = ByteArray(32) { 0x00 } // neutraler Salt (kann später gehärtet werden)
        val prk = hkdfExtract(salt, sharedSecret)
        return hkdfExpand(prk, info.toByteArray(Charsets.UTF_8), outLen)
    }

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(salt, HMAC_ALG))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(prk, HMAC_ALG))

        val result = ByteArray(outLen)
        var prev = ByteArray(0)
        var offset = 0
        var counter = 1.toByte()

        while (offset < outLen) {
            mac.reset()
            mac.update(prev)
            mac.update(info)
            mac.update(counter)

            prev = mac.doFinal()

            val remaining = outLen - offset
            val toCopy = minOf(remaining, prev.size)
            System.arraycopy(prev, 0, result, offset, toCopy)

            offset += toCopy
            counter = (counter + 1).toByte()
        }

        return result
    }
}

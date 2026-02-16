package com.securecall.app.ghostnet.crypto

import android.util.Log
import com.securecall.app.BuildConfig

/**
 * PATCH 247:
 * Skeleton für einen Crypto-Kontext pro GhostNet-Session.
 */
class SessionCryptoContext(
    private val keys: SessionKeys
) {

    // CRYPTO-04: ECDH Felder
    var localKeyPair: X25519KeyPair? = null
    var remotePublicKey: ByteArray? = null
    var sharedSecret: ByteArray? = null

    // CRYPTO-05: abgeleitete Schlüssel
    var masterKey: ByteArray? = null
    var sendKey: ByteArray? = null
    var recvKey: ByteArray? = null

    companion object {
        private const val TAG = "SESS_CRYPTO_CTX"

        @JvmStatic
        fun fromMockHandshake(): SessionCryptoContext {
            if (BuildConfig.DEBUG) Log.d(TAG, "fromMockHandshake(): creating context via mock handshake")
            val sk = SessionKeyDerivation.deriveFromMockHandshake()
            return SessionCryptoContext(sk)
        }
    }

    fun encryptOutbound(plain: ByteArray): ByteArray {
        if (BuildConfig.DEBUG) Log.d("SESS_CRYPTO_CTX", "encryptOutbound(): size=${plain.size} (NO REAL ENCRYPTION)")
        return plain
    }

    fun decryptInbound(cipher: ByteArray): ByteArray {
        if (BuildConfig.DEBUG) Log.d("SESS_CRYPTO_CTX", "decryptInbound(): size=${cipher.size} (NO REAL DECRYPTION)")
        return cipher
    }

    fun wipe() {
        sharedSecret?.fill(0)
        masterKey?.fill(0)
        sendKey?.fill(0)
        recvKey?.fill(0)
        sharedSecret = null
        masterKey = null
        sendKey = null
        recvKey = null
    }

    fun debugSummary(): String {
        return "rx=${keys.rxKey.size}, tx=${keys.txKey.size}, salt=${keys.salt.size}"
    }
}

package com.securecall.app.ghostnet.crypto

import android.util.Log

/**
 * PATCH 247:
 * Skeleton für einen Crypto-Kontext pro GhostNet-Session.
 *
 * Aufgaben (Zukunft):
 *  - SessionKeys halten (rx/tx/salt)
 *  - Outbound-Daten verschlüsseln
 *  - Inbound-Daten entschlüsseln
 *  - ggf. Key-Rotation / Re-Keying
 *
 * Aktueller Stand:
 *  - KEINE echte Kryptografie
 *  - encryptOutbound() / decryptInbound() geben Daten 1:1 durch
 */

class SessionCryptoContext(
    private val keys: SessionKeys
) {

    companion object {
        private const val TAG = "SESS_CRYPTO_CTX"

        /**
         * Hilfsfunktion: Erzeugt einen neuen Kontext aus einem
         * Mock-Handshake → SessionKeyDerivation-Kette.
         *
         * Später: echter Handshake statt Mock.
         */
        @JvmStatic
        fun fromMockHandshake(): SessionCryptoContext {
            Log.d(TAG, "fromMockHandshake(): creating context via mock handshake")
            val sk = SessionKeyDerivation.deriveFromMockHandshake()
            return SessionCryptoContext(sk)
        }
    }

    /**
     * Placeholder:
     * Outbound-Daten werden NICHT verschlüsselt, nur geloggt.
     */
    fun encryptOutbound(plain: ByteArray): ByteArray {
        Log.d(TAG, "encryptOutbound(): size=${plain.size} (NO REAL ENCRYPTION)")
        // TODO (CRYPTO-04): echte Verschlüsselung mit keys.txKey
        return plain
    }

    /**
     * Placeholder:
     * Inbound-Daten werden NICHT entschlüsselt, nur geloggt.
     */
    fun decryptInbound(cipher: ByteArray): ByteArray {
        Log.d(TAG, "decryptInbound(): size=${cipher.size} (NO REAL DECRYPTION)")
        // TODO (CRYPTO-04): echte Entschlüsselung mit keys.rxKey
        return cipher
    }

    /**
     * Optional: kurze Debug-Zusammenfassung der Key-Längen.
     */
    fun debugSummary(): String {
        return "rx=${keys.rxKey.size}, tx=${keys.txKey.size}, salt=${keys.salt.size}"
    }
}

    // CRYPTO-04: ECDH Felder
    var localKeyPair: X25519KeyPair? = null
    var remotePublicKey: ByteArray? = null
    var sharedSecret: ByteArray? = null

    // CRYPTO-05: abgeleitete Schlüssel
    var masterKey: ByteArray? = null
    var sendKey: ByteArray? = null
    var recvKey: ByteArray? = null

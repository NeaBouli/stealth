package com.securecall.app.ghostnet.crypto

import android.util.Log

/**
 * CRYPTO-23:
 * Platzhalter für Transport-Verschlüsselung.
 *
 * Aktuell: NO-OP (Eingabe = Ausgabe).
 * Später: echte Verschlüsselung (z.B. XChaCha20-Poly1305 / AES-GCM)
 * mit Session-Key, Nonce und vollständiger Authentizität.
 */
object WireCryptoStub {

    private const val TAG = "WIRE_CRYPTO"

    fun encryptPayload(plain: ByteArray): ByteArray {
        Log.d(TAG, "encryptPayload(): size=${plain.size}")
        // TODO: CRYPTO-REAL: hier echte Verschlüsselung einbauen
        return plain
    }

    fun decryptPayload(cipher: ByteArray): ByteArray {
        Log.d(TAG, "decryptPayload(): size=${cipher.size}")
        // TODO: CRYPTO-REAL: hier echte Entschlüsselung einbauen
        return cipher
    }
}

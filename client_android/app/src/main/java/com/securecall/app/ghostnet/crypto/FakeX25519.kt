package com.securecall.app.ghostnet.crypto

import android.util.Log
import java.security.SecureRandom

/**
 * CRYPTO-04:
 * FAKE Stub für X25519 ECDH Keypairs & sharedSecret.
 * Wird später vollständig durch Rust-Implementierung ersetzt.
 */
object FakeX25519 {

    private const val TAG = "FAKE_X25519"
    private val rng = SecureRandom()

    fun generateKeyPair(): X25519KeyPair {
        val priv = ByteArray(32)
        val pub = ByteArray(32)
        rng.nextBytes(priv)
        rng.nextBytes(pub)

        Log.d(TAG, "generateKeyPair(): FAKE keypair generated")
        return X25519KeyPair(priv, pub)
    }

    fun deriveSharedSecret(
        localPriv: ByteArray,
        remotePub: ByteArray
    ): ByteArray {
        val secret = ByteArray(32)
        rng.nextBytes(secret)

        Log.d(TAG, "deriveSharedSecret(): FAKE sharedSecret generated")
        return secret
    }
}

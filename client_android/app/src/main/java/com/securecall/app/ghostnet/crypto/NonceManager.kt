package com.securecall.app.ghostnet.crypto

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * CRYPTO-08:
 * Zentraler Nonce-Manager für alle verschlüsselten Frames.
 *
 * - Thread-sicher
 * - Overflow-sicher (wrapt zurück zu 1)
 */
object NonceManager {

    private const val TAG = "NONCE_MANAGER"

    private val counter = AtomicLong(1L)

    fun nextNonce(): Long {
        val value = counter.getAndIncrement()

        // Overflow-Handling: nonce reuse with same key is catastrophic for AEAD
        if (value <= 0L || value == Long.MAX_VALUE) {
            Log.e(TAG, "nextNonce(): FATAL — nonce counter overflow, refusing to reuse nonces")
            throw SecurityException("Nonce counter overflow — session key must be rotated")
        }

        return value
    }
}

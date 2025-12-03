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

        // Overflow-Handling: niemals 0 oder negatives
        if (value <= 0L || value == Long.MAX_VALUE) {
            Log.w(TAG, "nextNonce(): overflow detected, resetting counter to 1")
            counter.set(1L)
            return 1L
        }

        return value
    }
}

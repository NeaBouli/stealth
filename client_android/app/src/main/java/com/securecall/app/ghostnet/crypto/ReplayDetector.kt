package com.securecall.app.ghostnet.crypto

import android.util.Log

/**
 * CRYPTO-13:
 * Lightweight Replay Detector (Debug-Modus).
 */
object ReplayDetector {

    private const val TAG = "REPLAY_DETECTOR"

    private var lastNonce: Long = -1L

    fun check(nonce: Long) {
        when {
            lastNonce < 0L -> {
                lastNonce = nonce
                Log.d(TAG, "Init nonce=$nonce")
            }
            nonce == lastNonce -> {
                Log.w(TAG, "REPLAY DETECTED: nonce=$nonce (same as last)")
            }
            nonce < lastNonce -> {
                Log.e(TAG, "BACKWARD NONCE: nonce=$nonce < last=$lastNonce")
            }
            nonce > lastNonce -> {
                Log.d(TAG, "Nonce OK: $nonce (last was $lastNonce)")
                lastNonce = nonce
            }
        }
    }

    fun reset() {
        lastNonce = -1L
    }

    // CRYPTO-14: Security-aware check
    fun checkWithSecurity(nonce: Long) {
        when {
            lastNonce < 0L -> {
                lastNonce = nonce
                Log.d(TAG, "Init nonce=$nonce")
            }
            nonce == lastNonce -> {
                Log.w(TAG, "REPLAY DETECTED: nonce=$nonce")
                handleSecurity(nonce, "REPLAY")
            }
            nonce < lastNonce -> {
                Log.e(TAG, "BACKWARD NONCE: nonce=$nonce < last=$lastNonce")
                handleSecurity(nonce, "BACKWARD")
            }
            nonce > lastNonce -> {
                Log.d(TAG, "Nonce OK: $nonce")
                lastNonce = nonce
            }
        }
    }

    private fun securityReport(event: String, nonce: Long) {
        Log.w(TAG, "Security event: $event nonce=$nonce")
    }

    private fun handleSecurity(nonce: Long, type: String) {
        securityReport(type, nonce)
    }
}

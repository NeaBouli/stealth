package com.securecall.app.ghostnet.crypto

import android.util.Log

/**
 * CRYPTO-13:
 * Lightweight Replay Detector (Debug-Modus).
 *
 * - Hält höchsten Nonce-Wert fest
 * - Erkennt Replays, Rückwärtsbewegungen, Sprünge
 * - Keine harten Drops – nur Logging
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
}

    // CRYPTO-14: Security Mode Awareness
    private fun securityReport(event: String, nonce: Long) {
        val mode = com.securecall.app.ghostnet.security.SecurityStateMachine.getMode()
        when (mode) {
            com.securecall.app.ghostnet.security.SecurityMode.OFF -> { /* ignore */ }

            com.securecall.app.ghostnet.security.SecurityMode.LOG_ONLY -> {
                Log.w(TAG, "[LOG_ONLY] $event nonce=$nonce")
            }

            com.securecall.app.ghostnet.security.SecurityMode.STRICT -> {
                Log.e(TAG, "[STRICT] $event nonce=$nonce (flagged)")
            }

            com.securecall.app.ghostnet.security.SecurityMode.MANDATORY -> {
                Log.e(TAG, "[MANDATORY] $event nonce=$nonce (would drop later)")
            }

            com.securecall.app.ghostnet.security.SecurityMode.LOCKDOWN -> {
                Log.e(TAG, "[LOCKDOWN] $event nonce=$nonce (critical)")
            }
        }
    }

    // CRYPTO-14: Hook integration
    private fun handleSecurity(nonce: Long, type: String) {
        securityReport(type, nonce)
    }

    // CRYPTO-14: Insert security handling inside checks
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

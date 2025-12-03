package com.securecall.app.ghostnet.media.crypto

import android.util.Log

/**
 * CRYPTO-12:
 * Basale Validierung für eingehende CiphertextFrames.
 *
 * Ziele:
 * - Version prüfen
 * - Ciphertext-Länge prüfen
 * - Nonce grob sanity-checken
 *
 * Keine echte Kryptoverifikation, nur Struktur-/Range-Checks.
 */
object CiphertextValidator {

    private const val TAG = "CT_VALIDATOR"

    // Aktuell unterstützen wir nur Version 1
    private const val SUPPORTED_VERSION: Byte = 0x01

    // Obergrenze für Ciphertext-Länge (kann später policy-gesteuert werden)
    private const val MAX_CIPHERTEXT_LEN: Int = 64 * 1024 // 64 KiB

    fun isValid(frame: CiphertextFrame): Boolean {
        val header = frame.header
        val cipher = frame.ciphertext

        // Version
        if (header.version != SUPPORTED_VERSION) {
            Log.w(TAG, "Invalid version: ${header.version} (expected=$SUPPORTED_VERSION)")
            return false
        }

        // Länge
        val len = cipher.size
        if (len <= 0) {
            Log.w(TAG, "Invalid ciphertext length (<=0): $len")
            return false
        }
        if (len > MAX_CIPHERTEXT_LEN) {
            Log.w(TAG, "Ciphertext too large: $len > $MAX_CIPHERTEXT_LEN")
            return false
        }

        // Nonce: aktuell nur grober Check – später Anti-Replay/Window
        if (header.nonce < 0L) {
            Log.w(TAG, "Nonce negative (unexpected): ${header.nonce}")
            return false
        }

        // Alles ok
        Log.d(TAG, "CiphertextFrame valid: version=${header.version}, nonce=${header.nonce}, len=$len")
        return true
    }
}

package com.securecall.app.crypto

import android.util.Base64

/**
 * BACKEND-23 — Ephemeral Key Stub (MVP)
 *
 * Später ersetzen wir das durch X25519 (Curve25519).
 * Aktuell: generiert nur zufällige 32 Byte für KeyMaterial.
 */

object EphemeralKeyProvider {

    fun generateKeyMaterial(): ByteArray {
        val keyBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(keyBytes)
        return keyBytes
    }

    fun generateKeyMaterialBase64(): String {
        val keyBytes = generateKeyMaterial()
        val encoded = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        keyBytes.fill(0)
        return encoded
    }
}

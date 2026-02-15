package com.securecall.app.ghostnet.crypto

/**
 * CRYPTO-27:
 * Repräsentiert eine laufende Verschlüsselungs-Session.
 *
 * - sessionId: Logisch eindeutige ID der Gesprächs-/Daten-Session.
 * - keyId: Version/Index des verwendeten Schlüssels (z.B. für Rotation).
 * - rxKey: Empfangs-Schlüssel (remote -> lokal).
 * - txKey: Sende-Schlüssel (lokal -> remote).
 * - nonceCounter: lokaler Zähler für Nonces.
 *
 * Später:
 *  - getrennte Counter für RX/TX
 *  - Integration mit Rust/JNI (z.B. XChaCha20-Poly1305 / AES-GCM)
 */
data class SessionCipherContext(
    val sessionId: String,
    val keyId: Int,
    val rxKey: ByteArray,
    val txKey: ByteArray,
    private var nonceCounter: Long = 0L
) {

    @Synchronized
    fun nextNonce(): Long {
        nonceCounter += 1
        return nonceCounter
    }

    fun wipe() {
        rxKey.fill(0)
        txKey.fill(0)
        nonceCounter = 0L
    }
}

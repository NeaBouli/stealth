package com.securecall.app.ghostnet.media.crypto

/**
 * CRYPTO-10:
 * Repräsentiert einen verschlüsselten Frame, bestehend aus:
 *  - Header (Nonce, Version, etc.)
 *  - Ciphertext (verschlüsselte Audiodaten)
 *
 * Die On-Wire-Repräsentation (ByteArray) wird später über eine
 * Helper-Utility abgebildet. Für jetzt reicht diese Modellklasse.
 */
data class CiphertextFrame(
    val header: FrameHeader,
    val ciphertext: ByteArray
) {
    fun totalSize(): Int = ciphertext.size
}

package com.securecall.app.ghostnet.transport

/**
 * CRYPTO-30:
 * Frame für verschlüsselte Daten (post-encryption).
 *
 * NICHT MEDIAFRAME!
 * Dies ist ein separates Objekt, um die Architektur sauber zu halten.
 */
data class EncryptedFrame(
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)

package com.securecall.app.ghostnet.frame

/**
 * CRYPTO-21:
 * Repräsentiert ein Voice-Paket (z. B. PCM oder Codec-Bytes).
 */
data class AudioFrame(
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)

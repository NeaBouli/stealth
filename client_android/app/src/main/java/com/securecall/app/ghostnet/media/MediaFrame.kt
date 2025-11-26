package com.securecall.app.ghostnet.media

// BACKEND-63: Grundklasse für spätere Audioframes (encrypted or raw)
data class MediaFrame(
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)

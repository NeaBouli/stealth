package com.securecall.app.ghostnet.frame

/**
 * CRYPTO-21:
 * KeepAliveFrame wird regelmäßig gesendet, um Verbindungen am Leben zu halten.
 */
data class KeepAliveFrame(
    val timestamp: Long = System.currentTimeMillis()
)

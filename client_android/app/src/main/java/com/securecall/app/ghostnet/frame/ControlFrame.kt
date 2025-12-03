package com.securecall.app.ghostnet.frame

/**
 * CRYPTO-21:
 * ControlFrame dient Signalisierung innerhalb einer aktiven Session.
 */
data class ControlFrame(
    val code: Int,        // z. B. 100 = call-accept, 200 = call-end
    val info: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

package com.securecall.app.ghostnet.frame.body

/**
 * CRYPTO-38:
 * KeepAlive-Frames tragen später Sequenznummern und Timestamps,
 * im MVP nur Dummy.
 */
object KeepAliveBodyParser {

    data class KeepAliveInfo(val timestamp: Long)

    fun parse(body: ByteArray): KeepAliveInfo {
        return KeepAliveInfo(System.currentTimeMillis())
    }
}

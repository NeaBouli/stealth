package com.securecall.app.ghostnet.crypto.header

object WireHeaderParser {
    fun parse(raw: ByteArray): WireHeader? {
        if (raw.size < 9) return null
        val version = raw[0].toInt() and 0xFF
        var nonce = 0L
        for (i in 1..8) {
            nonce = (nonce shl 8) or (raw[i].toLong() and 0xFF)
        }
        return WireHeader(version, nonce)
    }
}

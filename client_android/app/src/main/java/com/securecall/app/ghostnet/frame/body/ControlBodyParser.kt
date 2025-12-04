package com.securecall.app.ghostnet.frame.body

/**
 * CRYPTO-38:
 * Control-Frames enthalten einfache "code:text"-Struktur.
 */
object ControlBodyParser {

    data class ControlMessage(val code: Int, val text: String)

    fun parse(body: ByteArray): ControlMessage? {
        val s = String(body)
        val parts = s.split(":", limit = 2)
        if (parts.size != 2) return null

        val code = parts[0].toIntOrNull() ?: return null
        val msg = parts[1]

        return ControlMessage(code, msg)
    }
}

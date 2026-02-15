package com.securecall.app.ghostnet.frame

/**
 * CRYPTO-37:
 * FrameType anhand der Header-Flags bestimmt.
 */
enum class FrameType(val id: Byte) {
    AUDIO(0x01),
    CONTROL(0x02),
    KEEPALIVE(0x03),
    UNKNOWN(0xFF.toByte());

    companion object {
        fun fromId(id: Byte): FrameType? = values().firstOrNull { it.id == id }
    }
}

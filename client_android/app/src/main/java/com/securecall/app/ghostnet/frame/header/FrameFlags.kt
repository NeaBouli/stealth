package com.securecall.app.ghostnet.frame.header

/**
 * CRYPTO-34:
 * Bitmask-Flags für FrameHeaderV1.
 */
object FrameFlags {
    const val AUDIO = 0x01
    const val CONTROL = 0x02
    const val KEEPALIVE = 0x04

    // spätere Erweiterungen:
    // const val VIDEO = 0x08
    // const val EXTENDED = 0x10
}

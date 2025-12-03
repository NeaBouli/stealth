package com.securecall.app.ghostnet.frame

/**
 * CRYPTO-37:
 * FrameType anhand der Header-Flags bestimmt.
 */
enum class FrameType {
    AUDIO,
    CONTROL,
    KEEPALIVE,
    UNKNOWN
}

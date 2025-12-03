package com.securecall.app.ghostnet.frame

import com.securecall.app.ghostnet.frame.header.FrameFlags

/**
 * CRYPTO-37:
 * Wandelt die Flags (Byte 1 in FrameHeaderV1) in ein FrameType-Enum um.
 */
object FrameTypeResolver {

    fun resolve(flags: Int): FrameType {
        return when {
            flags and FrameFlags.AUDIO != 0 -> FrameType.AUDIO
            flags and FrameFlags.CONTROL != 0 -> FrameType.CONTROL
            flags and FrameFlags.KEEPALIVE != 0 -> FrameType.KEEPALIVE
            else -> FrameType.UNKNOWN
        }
    }
}

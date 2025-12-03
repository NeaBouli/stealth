package com.securecall.app.ghostnet.frame.header

/**
 * CRYPTO-34:
 * Minimaler, zukunftsorientierter 4-Byte Header.
 *
 * Layout:
 *   byte 0: VERSION (0x01)
 *   byte 1: FLAGS   (Bitfield)
 *   byte 2: KEY_ID
 *   byte 3: NONCE_PREFIX (High 8 bits von Nonce)
 */
data class FrameHeaderV1(
    val version: Int,
    val flags: Int,
    val keyId: Int,
    val noncePrefix: Int
) {

    fun toBytes(): ByteArray {
        return byteArrayOf(
            version.toByte(),
            flags.toByte(),
            keyId.toByte(),
            noncePrefix.toByte()
        )
    }

    companion object {
        const val VERSION = 1

        fun parse(bytes: ByteArray): FrameHeaderV1? {
            if (bytes.size < 4) return null
            return FrameHeaderV1(
                version = bytes[0].toInt() and 0xFF,
                flags = bytes[1].toInt() and 0xFF,
                keyId = bytes[2].toInt() and 0xFF,
                noncePrefix = bytes[3].toInt() and 0xFF
            )
        }
    }
}

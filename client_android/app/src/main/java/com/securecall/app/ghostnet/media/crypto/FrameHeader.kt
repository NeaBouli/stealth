package com.securecall.app.ghostnet.media.crypto

/**
 * CRYPTO-07: Frame Header Format
 * version: 1 byte
 * nonce: 8 bytes (big endian)
 */
data class FrameHeader(
    val version: Byte = 1,
    val nonce: Long
)

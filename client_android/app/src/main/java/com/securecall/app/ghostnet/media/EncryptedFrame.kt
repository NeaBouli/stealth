package com.securecall.app.ghostnet.media

data class EncryptedFrame(
    val version: Byte,
    val nonce: Long,
    val ciphertext: ByteArray
)

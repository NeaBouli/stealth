package com.securecall.app.ghostnet.crypto

/**
 * CRYPTO-04: ECDH Keypair Struktur
 * Diese Struktur wird später von echter Rust-Crypto gefüllt.
 */
data class X25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray
)

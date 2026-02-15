package com.securecall.app.ghostnet.crypto

import android.util.Log
import com.securecall.crypto.CoreCrypto

/**
 * Session Cipher Engine — verschluesselt und entschluesselt Frames
 * via Rust CoreCrypto (XChaCha20-Poly1305).
 *
 * Fallback auf No-Op wenn die Native Library nicht geladen werden konnte.
 */
object SessionCipherEngine {

    private const val TAG = "SESSION_CIPHER"

    fun encrypt(
        ctx: SessionCipherContext,
        plain: ByteArray
    ): ByteArray {
        val nonce = ctx.nextNonce()
        val header = SessionCipherHeader.create(ctx.keyId, nonce)

        Log.d(TAG, "encrypt(): sessionId=${ctx.sessionId}, keyId=${ctx.keyId}, nonce=$nonce, plainSize=${plain.size}")

        if (!CoreCrypto.isNativeAvailable()) {
            Log.e(TAG, "encrypt(): FATAL — native crypto not available, refusing to send plaintext")
            throw SecurityException("Native crypto engine not available — cannot encrypt")
        }

        val encrypted = CoreCrypto.encrypt(ctx.txKey, plain)
        if (encrypted == null || encrypted.isEmpty()) {
            Log.e(TAG, "encrypt(): FATAL — native encryption returned null/empty")
            throw SecurityException("Encryption failed — refusing to send plaintext")
        }

        return encrypted
    }

    fun decrypt(
        ctx: SessionCipherContext,
        cipher: ByteArray
    ): ByteArray {
        Log.d(TAG, "decrypt(): sessionId=${ctx.sessionId}, keyId=${ctx.keyId}, cipherSize=${cipher.size}")

        if (!CoreCrypto.isNativeAvailable()) {
            Log.e(TAG, "decrypt(): FATAL — native crypto not available, refusing to return ciphertext as plaintext")
            throw SecurityException("Native crypto engine not available — cannot decrypt")
        }

        val decrypted = CoreCrypto.decrypt(ctx.rxKey, cipher)
        if (decrypted == null || decrypted.isEmpty()) {
            Log.e(TAG, "decrypt(): FATAL — native decryption returned null/empty")
            throw SecurityException("Decryption failed — data may be corrupted or tampered")
        }

        return decrypted
    }

    // CRYPTO-34: HeaderV1 Builder
    fun buildFrameHeaderV1(ctx: SessionCipherContext, flags: Int, nonce: Long): ByteArray {
        val prefix = ((nonce shr 8) and 0xFF).toInt()
        val header = com.securecall.app.ghostnet.frame.header.FrameHeaderV1(
            version = com.securecall.app.ghostnet.frame.header.FrameHeaderV1.VERSION,
            flags = flags,
            keyId = ctx.keyId,
            noncePrefix = prefix
        )
        return header.toBytes()
    }

    // CRYPTO-34: EncryptPipeline mit echtem AEAD
    fun encryptFrameV1(
        ctx: SessionCipherContext,
        plain: ByteArray,
        flags: Int
    ): ByteArray {
        val nonce = ctx.nextNonce()
        val header = buildFrameHeaderV1(ctx, flags, nonce)
        val encryptedPayload = encrypt(ctx, plain)
        return header + encryptedPayload
    }

    // CRYPTO-34: DecryptPipeline
    fun decryptFrameV1(
        ctx: SessionCipherContext,
        cipher: ByteArray
    ): ByteArray {
        if (cipher.size < 4) return cipher

        val header = com.securecall.app.ghostnet.frame.header.FrameHeaderV1.parse(cipher)
        if (header == null) {
            Log.e(TAG, "decryptFrameV1(): invalid header")
            return cipher
        }

        Log.d(TAG, "decryptFrameV1(): flags=${header.flags} keyId=${header.keyId} prefix=${header.noncePrefix}")

        val payload = cipher.copyOfRange(4, cipher.size)
        return decrypt(ctx, payload)
    }

    // CRYPTO-35: Flag-spezifische Encrypt-Wrapper

    fun encryptAudioFrameV1(
        ctx: SessionCipherContext,
        plain: ByteArray
    ): ByteArray {
        return encryptFrameV1(ctx, plain, com.securecall.app.ghostnet.frame.header.FrameFlags.AUDIO)
    }

    fun encryptControlFrameV1(
        ctx: SessionCipherContext,
        plain: ByteArray
    ): ByteArray {
        return encryptFrameV1(ctx, plain, com.securecall.app.ghostnet.frame.header.FrameFlags.CONTROL)
    }

    fun encryptKeepAliveFrameV1(
        ctx: SessionCipherContext,
        plain: ByteArray
    ): ByteArray {
        return encryptFrameV1(ctx, plain, com.securecall.app.ghostnet.frame.header.FrameFlags.KEEPALIVE)
    }
}

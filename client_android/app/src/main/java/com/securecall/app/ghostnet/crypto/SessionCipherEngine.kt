package com.securecall.app.ghostnet.crypto

import android.util.Log

/**
 * CRYPTO-27:
 * Stub-Implementierung für das Session-Ciphering.
 *
 * Ziel:
 *  - zentrale Stelle für Encrypt/Decrypt mit SessionCipherContext
 *  - Integration eines Key-Version-Headers (SessionCipherHeader)
 *
 * Derzeit:
 *  - KEINE echte Kryptografie
 *  - Logging + Rückgabe der Originaldaten
 *
 * Später:
 *  - JNI/Rust Binding (XChaCha20-Poly1305 oder AES-GCM)
 *  - Header in den Ciphertext prefixen (magic + keyId + nonce)
 *  - MAC-Verification etc.
 */
object SessionCipherEngine {

    private const val TAG = "SESSION_CIPHER"

    fun encrypt(
        ctx: SessionCipherContext,
        plain: ByteArray
    ): ByteArray {
        val nonce = ctx.nextNonce()
        val header = SessionCipherHeader.create(ctx.keyId, nonce)

        Log.d(
            TAG,
            "encrypt(): sessionId=${ctx.sessionId}, keyId=${ctx.keyId}, nonce=$nonce, plainSize=${plain.size}, headerMagic=${header.magic}"
        )

        // !!! PLACEHOLDER !!!
        // Später:
        //  1) Header → ByteArray serialisieren
        //  2) plain → AEAD encrypt(txKey, nonce)
        //  3) result = headerBytes + ciphertext
        //
        // Jetzt: wir geben einfach das Plaintext-Array zurück.
        return plain
    }

    fun decrypt(
        ctx: SessionCipherContext,
        cipher: ByteArray
    ): ByteArray {
        Log.d(
            TAG,
            "decrypt(): sessionId=${ctx.sessionId}, keyId=${ctx.keyId}, cipherSize=${cipher.size}"
        )

        // !!! PLACEHOLDER !!!
        // Später:
        //  1) Header vom Anfang des Ciphertext abtrennen & prüfen (magic, keyId, nonce)
        //  2) Ciphertext-Teil via AEAD mit rxKey entschlüsseln
        //
        // Jetzt: wir geben einfach den "Ciphertext" unverändert zurück.
        return cipher
    }
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

    // CRYPTO-34: vollständige Stub-EncryptPipeline
    fun encryptFrameV1(
        ctx: SessionCipherContext,
        plain: ByteArray,
        flags: Int
    ): ByteArray {
        val nonce = ctx.nextNonce()

        val header = buildFrameHeaderV1(ctx, flags, nonce)
        val encryptedPayload = encrypt(ctx, plain)  // Stub

        return header + encryptedPayload
    }

    // CRYPTO-34: Stub-DecryptPipeline (Header auslesen)
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
        return decrypt(ctx, payload)  // Stub
    }

    // CRYPTO-35: Flag-spezifische Encrypt-Wrapper für FrameHeaderV1

    fun encryptAudioFrameV1(
        ctx: SessionCipherContext,
        plain: ByteArray
    ): ByteArray {
        return encryptFrameV1(
            ctx,
            plain,
            com.securecall.app.ghostnet.frame.header.FrameFlags.AUDIO
        )
    }

    fun encryptControlFrameV1(
        ctx: SessionCipherContext,
        plain: ByteArray
    ): ByteArray {
        return encryptFrameV1(
            ctx,
            plain,
            com.securecall.app.ghostnet.frame.header.FrameFlags.CONTROL
        )
    }

    fun encryptKeepAliveFrameV1(
        ctx: SessionCipherContext,
        plain: ByteArray
    ): ByteArray {
        return encryptFrameV1(
            ctx,
            plain,
            com.securecall.app.ghostnet.frame.header.FrameFlags.KEEPALIVE
        )
    }

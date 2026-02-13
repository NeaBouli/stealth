package com.securecall.app.ghostnet.media.crypto

import android.util.Log
import com.securecall.app.ghostnet.crypto.SessionCipherContext
import com.securecall.app.ghostnet.crypto.SessionCipherEngine

/**
 * CRYPTO-28:
 * Stub für die Encrypt-Seite.
 */
object MediaEncryptor {

    private const val TAG = "MEDIA_ENCRYPT"

    fun encrypt(ctx: SessionCipherContext, pcm: ByteArray): ByteArray {
        Log.d(TAG, "encrypt(): pcmSize=${pcm.size}")
        return SessionCipherEngine.encrypt(ctx, pcm)
    }

    fun encrypt(pcm: ByteArray, ctx: com.securecall.app.ghostnet.crypto.SessionCryptoContext?): ByteArray {
        Log.d(TAG, "encrypt(): pcmSize=${pcm.size} (SessionCryptoContext)")
        return pcm
    }

    fun encrypt(frame: com.securecall.app.ghostnet.media.MediaFrame): ByteArray {
        Log.d(TAG, "encrypt(): frame size=${frame.data.size}")
        return frame.data
    }

    fun encryptWithKey(pcm: ByteArray, ctx: com.securecall.app.ghostnet.crypto.SessionCryptoContext?): ByteArray {
        Log.d(TAG, "encryptWithKey(): pcmSize=${pcm.size}")
        return pcm
    }

    // CRYPTO-39: FrameV1 Builders
    fun buildAndEncryptAudioFrameV1(ctx: SessionCipherContext, pcm: ByteArray): ByteArray {
        val type = com.securecall.app.ghostnet.frame.FrameType.AUDIO
        return FrameHeaderUtils.encryptFrameV1ForType(ctx, type, pcm)
    }

    fun buildAndEncryptControlFrameV1(ctx: SessionCipherContext, code: Int, text: String): ByteArray {
        val payload = "$code:$text".toByteArray()
        val type = com.securecall.app.ghostnet.frame.FrameType.CONTROL
        return FrameHeaderUtils.encryptFrameV1ForType(ctx, type, payload)
    }

    fun buildAndEncryptKeepAliveFrameV1(ctx: SessionCipherContext): ByteArray {
        val payload = ByteArray(0)
        val type = com.securecall.app.ghostnet.frame.FrameType.KEEPALIVE
        return FrameHeaderUtils.encryptFrameV1ForType(ctx, type, payload)
    }

    fun buildDummyCallInviteFrameV1(ctx: SessionCipherContext): ByteArray {
        return buildAndEncryptControlFrameV1(ctx, 100, "CALL-INVITE")
    }
}

package com.securecall.app.ghostnet.media.crypto

import android.util.Log
import com.securecall.app.ghostnet.crypto.SessionCipherContext
import com.securecall.app.ghostnet.crypto.SessionCipherEngine

/**
 * CRYPTO-28:
 * Einfacher Stub für die Encrypt-Seite.
 *
 * Später:
 *   PCM → encode() → encrypt() → TransportFrame
 */
object MediaEncryptor {

    private const val TAG = "MEDIA_ENCRYPT"

    fun encrypt(ctx: SessionCipherContext, pcm: ByteArray): ByteArray {
        Log.d(TAG, "encrypt(): pcmSize=${pcm.size}")
        return SessionCipherEngine.encrypt(ctx, pcm)
    }
}

// CRYPTO-39: AUDIO-FrameV1 Builder
fun buildAndEncryptAudioFrameV1(
    ctx: com.securecall.app.ghostnet.crypto.SessionCipherContext,
    pcm: ByteArray
): ByteArray {
    // später: Opus-Encoding/Jitterbuffer etc.
    val type = com.securecall.app.ghostnet.frame.FrameType.AUDIO
    return com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.encryptFrameV1ForType(
        ctx,
        type,
        pcm
    )
}

// CRYPTO-39: CONTROL-FrameV1 Builder
fun buildAndEncryptControlFrameV1(
    ctx: com.securecall.app.ghostnet.crypto.SessionCipherContext,
    code: Int,
    text: String
): ByteArray {
    val payload = "$code:$text".toByteArray()
    val type = com.securecall.app.ghostnet.frame.FrameType.CONTROL
    return com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.encryptFrameV1ForType(
        ctx,
        type,
        payload
    )
}

// CRYPTO-39: KEEPALIVE-FrameV1 Builder (MVP: leerer Body)
fun buildAndEncryptKeepAliveFrameV1(
    ctx: com.securecall.app.ghostnet.crypto.SessionCipherContext
): ByteArray {
    val payload = ByteArray(0)
    val type = com.securecall.app.ghostnet.frame.FrameType.KEEPALIVE
    return com.securecall.app.ghostnet.media.crypto.FrameHeaderUtils.encryptFrameV1ForType(
        ctx,
        type,
        payload
    )
}

// CRYPTO-39: Debug-Helfer – Dummy CALL-INVITE Control-Frame
fun buildDummyCallInviteFrameV1(
    ctx: com.securecall.app.ghostnet.crypto.SessionCipherContext
): ByteArray {
    return buildAndEncryptControlFrameV1(
        ctx,
        100,
        "CALL-INVITE"
    )
}

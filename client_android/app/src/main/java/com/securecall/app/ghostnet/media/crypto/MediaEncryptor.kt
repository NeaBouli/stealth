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

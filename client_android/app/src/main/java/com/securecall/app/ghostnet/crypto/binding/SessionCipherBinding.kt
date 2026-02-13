package com.securecall.app.ghostnet.crypto.binding

import android.util.Log
import com.securecall.app.ghostnet.crypto.SessionCipherContext
import com.securecall.app.ghostnet.crypto.SessionCipherEngine
import com.securecall.app.ghostnet.media.MediaFrame

/**
 * CRYPTO-28:
 * Bindeglied zwischen Transport-Frames und Media-Schicht.
 */
object SessionCipherBinding {

    private const val TAG = "SESSION_BINDING"

    @Volatile
    var activeSession: SessionCipherContext? = null

    fun decryptFrame(frame: MediaFrame): ByteArray {
        val session = activeSession
        if (session == null) {
            Log.e(TAG, "decryptFrame(): no active session → dropping frame")
            return ByteArray(0)
        }
        Log.d(TAG, "decryptFrame(): sessionId=${session.sessionId}, keyId=${session.keyId}, size=${frame.data.size}")
        return SessionCipherEngine.decrypt(session, frame.data)
    }

    fun encryptPcm(pcm: ByteArray): ByteArray {
        val session = activeSession
        if (session == null) {
            Log.e(TAG, "encryptPcm(): no active session → return plain")
            return pcm
        }
        return SessionCipherEngine.encrypt(session, pcm)
    }

    // CRYPTO-34: FrameV1 encrypt/decrypt
    fun encryptFrameV1(ctx: SessionCipherContext, data: ByteArray, flags: Int): ByteArray {
        return SessionCipherEngine.encryptFrameV1(ctx, data, flags)
    }

    fun decryptFrameV1(ctx: SessionCipherContext, data: ByteArray): ByteArray {
        return SessionCipherEngine.decryptFrameV1(ctx, data)
    }

    // CRYPTO-35: Flag-spezifische Binding-Wrapper
    fun encryptAudioFrameV1(data: ByteArray): ByteArray {
        val ctx = activeSession ?: return data
        return SessionCipherEngine.encryptAudioFrameV1(ctx, data)
    }

    fun encryptControlFrameV1(data: ByteArray): ByteArray {
        val ctx = activeSession ?: return data
        return SessionCipherEngine.encryptControlFrameV1(ctx, data)
    }

    fun encryptKeepAliveFrameV1(data: ByteArray): ByteArray {
        val ctx = activeSession ?: return data
        return SessionCipherEngine.encryptKeepAliveFrameV1(ctx, data)
    }
}

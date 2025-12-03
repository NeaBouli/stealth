package com.securecall.app.ghostnet.media.native

import android.util.Log

/**
 * PATCH 215:
 * JNI / Native Hooks für späteren Opus Decoder (Rust oder libopus).
 *
 * Jetzt:
 *  - keine echte Native-Funktion
 *  - reine Stubs, die sicher laufen
 *  - Log-Ausgabe statt Verarbeitung
 *
 * Später:
 *  - Rust via JNI
 *  - libopus C++ native decoder
 *  - Hardware-Acceleration (falls gewünscht)
 */
object NativeOpus {

    private const val TAG = "NATIVE_OPUS"

    init {
        try {
            System.loadLibrary("securecall")
            Log.d(TAG, "Native library loaded")
        } catch (t: Throwable) {
            Log.w(TAG, "Native library NOT loaded (expected in skeleton phase)")
        }
    }

    external fun nativeInit(sampleRate: Int, channels: Int): Long
    external fun nativeDecode(handle: Long, data: ByteArray): ShortArray
    external fun nativeRelease(handle: Long)

    // Skeleton fallback
    fun fakeDecode(data: ByteArray): ShortArray {
        Log.d(TAG, "fakeDecode(): using Kotlin fallback")
        val pcm = ShortArray(data.size / 2)
        var i = 0
        var j = 0
        while (i < data.size - 1) {
            val low = data[i].toInt() and 0xFF
            val high = data[i + 1].toInt() shl 8
            pcm[j] = (high or low).toShort()
            i += 2
            j += 1
        }
        return pcm
    }
}

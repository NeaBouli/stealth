package com.securecall.app.ghostnet.media.decode

import android.util.Log

/**
 * BACKEND-65:
 * Placeholder für Opus-Decoding.
 * Später: JNI → libopus (native).
 */
object MediaDecoder {

    private const val TAG = "MEDIA_DECODE"

    fun decode(bytes: ByteArray): ShortArray {
        Log.d(TAG, "decode(): called, size=${bytes.size}")

        // !!! PLACEHOLDER !!!
        // Wir geben eine Dummy-Audio-Waveform zurück.
        // Später: Opus.decode()
        val dummy = ShortArray(160) // 20ms @ 8kHz (nur symbolisch)
        return dummy
    }
}

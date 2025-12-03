package com.securecall.app.ghostnet.media.decoder

import android.util.Log
import com.securecall.app.ghostnet.media.codec.OpusDecoder

/**
 * PATCH 214:
 * AudioDecoder leitet ByteArray → OpusDecoder → PCM ShortArray.
 *
 * Noch Fake-Daten, aber zentrale Pipeline.
 */
object AudioDecoder {

    private const val TAG = "AUDIO_DECODER"

    fun init() {
        Log.d(TAG, "init()")
        OpusDecoder.init()
    }

    fun release() {
        Log.d(TAG, "release()")
        OpusDecoder.release()
    }

    /**
     * Haupt-Funktion: encoded -> PCM.
     * Noch Fake-Daten, aber Struktur ist produktionsreif.
     */
    fun decode(encoded: ByteArray): ShortArray {
        Log.d(TAG, "decode(): got ${encoded.size} bytes")
        return OpusDecoder.decode(encoded) // Fake → echte Integration später
    }
}

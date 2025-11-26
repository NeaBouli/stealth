package com.securecall.app.ghostnet.media.audio

import android.util.Log
import com.securecall.app.ghostnet.media.MediaFrame

/**
 * PATCH 209:
 * Skeleton für zukünftigen Audio-Decoder (Opus).
 *
 * Aktuell:
 *  - Keine echte Decodierung
 *  - Gibt Fake-PCM zurück
 *  - Lifecycle start/stop + Logs
 */
object AudioDecoder {

    private const val TAG = "AUDIO_DECODER"
    private var running = false

    fun start() {
        if (running) {
            Log.w(TAG, "start(): already running")
            return
        }
        running = true
        Log.d(TAG, "AudioDecoder STARTED")
    }

    fun stop() {
        if (!running) {
            Log.w(TAG, "stop(): already stopped")
            return
        }
        running = false
        Log.d(TAG, "AudioDecoder STOPPED")
    }

    fun isRunning(): Boolean = running

    /**
     * Nimmt ein entschlüsseltes MediaFrame entgegen
     * und produziert Fake-PCM.
     */
    fun decode(frame: MediaFrame): ShortArray {
        if (!running) {
            Log.w(TAG, "decode(): called while not running")
        }

        // FAKE-PCM generieren (später Opus-Decode)
        val pcm = ShortArray(frame.data.size / 2) { i ->
            // Dummy: Bytes → Short
            ((frame.data[i * 2].toInt() shl 8) or (frame.data[i * 2 + 1].toInt() and 0xFF)).toShort()
        }

        Log.d(TAG, "decode(): frame=${frame.data.size} bytes → pcm=${pcm.size} samples (FAKE)")
        return pcm
    }
}

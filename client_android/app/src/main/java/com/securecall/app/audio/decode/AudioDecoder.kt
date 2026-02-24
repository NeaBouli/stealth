package com.securecall.app.audio.decode

import android.util.Log

// BACKEND-45 / ANDROID-03:
// Platzhalter für echte Audio-Decoding-Pipeline.
object AudioDecoder {

    private const val TAG = "AUDIO_DECODER"

    fun decode(frame: ByteArray) {
        Log.d(TAG, "Received AudioFrame for decode (len=${frame.size}) [placeholder]")

        // BACKEND-47: Ensure output initialized
        ensureOutputInit()

        // BACKEND-48: Push to JitterBuffer
        pushToJitter(frame)

        // BACKEND-48: Pull from JitterBuffer and forward
        val ready = pullFromJitter()
        if (ready != null) forwardToOutput(ready)

        // BACKEND-46: Forward to output
        forwardToOutput(frame)
    }

    // BACKEND-46: Weitergabe an AudioOutput
    fun forwardToOutput(decoded: ByteArray) {
        com.securecall.app.audio.output.AudioOutput.play(decoded)
    }

    // BACKEND-47: Initialisierung sicherstellen
    fun ensureOutputInit() {
        com.securecall.app.audio.output.AudioOutput.initTrack()
    }

    // BACKEND-48: JitterBuffer push (placeholder — real pipeline uses WebSocketService jitter playout)
    private fun pushToJitter(frame: ByteArray) {
        // JitterBuffer now uses ShortArray (decoded PCM); this placeholder is unused
    }

    // BACKEND-48: Frame aus JitterBuffer holen
    private fun pullFromJitter(): ByteArray? {
        // JitterBuffer now uses ShortArray (decoded PCM); this placeholder is unused
        return null
    }
}

package com.securecall.app.audio.decode

import android.util.Log

// BACKEND-45 / ANDROID-03:
// Platzhalter für echte Audio-Decoding-Pipeline.
// Später: PCM, Opus, Decrypt, Jitter-Buffer, AudioTrack-Output.
object AudioDecoder {

    private const val TAG = "AUDIO_DECODER"

    fun decode(frame: ByteArray) {
        // Nur Debug — kein echtes Decoding
        Log.d(TAG, "Received AudioFrame for decode (len=${frame.size}) [placeholder]")

        // später:
        // 1) decrypt()
        // 2) jitterBuffer.push()
        // 3) opusDecoder.decode()
        // 4) audioTrack.play()
    }
}

    // BACKEND-46: Weitergabe an AudioOutput (placeholder)
    fun forwardToOutput(decoded: ByteArray) {
        com.securecall.app.audio.output.AudioOutput.play(decoded)
    }

    // BACKEND-46: Dummy-Verkettung
    // Statt echtem Decoding: Weiterreichen des Eingangframes
    forwardToOutput(frame)

    // BACKEND-47: Initialisierung sicherstellen
    fun ensureOutputInit() {
        com.securecall.app.audio.output.AudioOutput.initTrack()
    }

    // BACKEND-47:	Init-Automatik
    ensureOutputInit()

    // BACKEND-48: JitterBuffer push
    private fun pushToJitter(frame: ByteArray) {
        com.securecall.app.audio.jitter.JitterBuffer.push(frame)
    }

    // BACKEND-48: Push Frame in JitterBuffer
    pushToJitter(frame)

    // BACKEND-48: Frame aus JitterBuffer holen
    private fun pullFromJitter(): ByteArray? {
        return com.securecall.app.audio.jitter.JitterBuffer.pop()
    }

    // BACKEND-48: Platzhalter — wenn Jitter was liefert → Output
    val ready = pullFromJitter()
    if (ready != null) forwardToOutput(ready)

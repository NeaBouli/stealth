package com.securecall.app.audio.capture

import android.util.Log

// BACKEND-40 / ANDROID-03:
// Platzhalter für Audio-Aufnahme — OHNE Mikrofonzugriff.
// Liefert nur Dummy-Daten (64 Bytes) für Transporttests.
class AudioCapturePlaceholder {

    @Volatile
    private var running = false

    private val TAG = "AUDIO_CAPTURE_PLACEHOLDER"

    fun start() {
        running = true
        Log.d(TAG, "Audio capture started (placeholder)")
    }

    fun stop() {
        running = false
        Log.d(TAG, "Audio capture stopped")
    }

    // Eine Dummy-Audio-"Probe" erzeugen
    fun generateFakeAudioFrame(): ByteArray {
        val bytes = ByteArray(64) { 0x33 }
        Log.d(TAG, "Generated fake PCM frame (${bytes.size} bytes)")
        return bytes
    }
}

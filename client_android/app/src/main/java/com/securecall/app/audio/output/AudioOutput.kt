package com.securecall.app.audio.output

import android.util.Log

// BACKEND-46 / ANDROID-03:
// Platzhalter für AudioTrack-basierte Audioausgabe.
object AudioOutput {

    private const val TAG = "AUDIO_OUTPUT"
    private var initialized = false

    fun play(frame: ByteArray) {
        Log.d(TAG, "AudioOutput received PCM frame (len=${frame.size}) [placeholder]")
    }

    fun init() {
        Log.d(TAG, "AudioOutput init [placeholder]")
    }

    fun release() {
        Log.d(TAG, "AudioOutput release [placeholder]")
    }

    // BACKEND-47: AudioTrack Setup (Placeholder)
    fun initTrack() {
        if (initialized) {
            Log.d(TAG, "AudioTrack already initialized (placeholder)")
            return
        }

        Log.d(TAG, "AudioTrack init [placeholder only] - sampleRate=" +
                com.securecall.app.audio.output.config.AudioOutputConfig.SAMPLE_RATE)

        initialized = true
    }

    fun releaseTrack() {
        if (!initialized) return
        Log.d(TAG, "AudioTrack release [placeholder]")
        initialized = false
    }
}

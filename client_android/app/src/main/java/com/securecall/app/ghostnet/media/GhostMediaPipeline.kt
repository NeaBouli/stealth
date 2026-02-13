package com.securecall.app.ghostnet.media

import android.util.Log

/**
 * PATCH 207 / 209 / 210:
 * Skeleton für die Media-Pipeline (Decrypt → Decode → Playback).
 */
object GhostMediaPipeline {

    private const val TAG = "MEDIA_PIPELINE"

    private var running: Boolean = false

    fun start() {
        if (running) return
        running = true
        Log.d(TAG, "MediaPipeline STARTED")

        startDecoder()
        startPlayback()
    }

    fun stop() {
        if (!running) return
        running = false
        Log.d(TAG, "MediaPipeline STOPPED")

        stopPlayback()
        stopDecoder()
    }

    fun isRunning(): Boolean = running

    // PATCH 209: Decoder starten/stoppen
    private fun startDecoder() {
        com.securecall.app.ghostnet.media.audio.AudioDecoder.start()
    }

    private fun stopDecoder() {
        com.securecall.app.ghostnet.media.audio.AudioDecoder.stop()
    }

    // PATCH 210: AudioPlayback Start/Stop
    private fun startPlayback() {
        com.securecall.app.ghostnet.media.audio.AudioPlayback.start()
    }

    private fun stopPlayback() {
        com.securecall.app.ghostnet.media.audio.AudioPlayback.stop()
    }
}

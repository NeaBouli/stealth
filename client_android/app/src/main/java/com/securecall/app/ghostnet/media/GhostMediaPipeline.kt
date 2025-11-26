package com.securecall.app.ghostnet.media

import android.util.Log

/**
 * PATCH 207:
 * Skeleton für die Media-Pipeline (Decrypt → Decode → Playback).
 *
 * Aktuell:
 *  - Nur Start/Stop-Flags
 *  - Nur Logging
 *  - Noch keine echten Audio-Operationen
 */
object GhostMediaPipeline {

    private const val TAG = "MEDIA_PIPELINE"

    private var running: Boolean = false

    fun start() {
        if (running) {
            Log.w(TAG, "start(): already running")
            return
        }
        running = true
        Log.d(TAG, "MediaPipeline STARTED")

        // TODO (später):
        // - AudioDecoder-Thread starten
        // - Decrypt/Decode-Queues verbinden
        // - Playback initialisieren (AudioTrack)
    }

    fun stop() {
        if (!running) {
            Log.w(TAG, "stop(): already stopped")
            return
        }
        running = false
        Log.d(TAG, "MediaPipeline STOPPED")

        // TODO (später):
        // - Decoder-Threads sauber stoppen
        // - Queues flushen
        // - AudioTrack freigeben
    }

    fun isRunning(): Boolean = running
}

    // PATCH 209 — Decoder starten/stoppen
    private fun startDecoder() {
        com.securecall.app.ghostnet.media.audio.AudioDecoder.start()
    }

    private fun stopDecoder() {
        com.securecall.app.ghostnet.media.audio.AudioDecoder.stop()
    }

    // Start/Stop erweitern
    fun start() {
        if (running) return
        running = true
        Log.d(TAG, "MediaPipeline STARTED")

        startDecoder()
    }

    fun stop() {
        if (!running) return
        running = false
        Log.d(TAG, "MediaPipeline STOPPED")

        stopDecoder()
    }

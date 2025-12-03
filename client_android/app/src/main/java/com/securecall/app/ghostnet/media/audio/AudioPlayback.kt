package com.securecall.app.ghostnet.media.audio

import android.util.Log

/**
 * PATCH 210:
 * AudioTrack Playback Skeleton.
 *
 * Noch keine echte Audio-Ausgabe:
 *  - Keine AudioTrack-Instanz
 *  - Keine PCM-Ausgabe
 * Nur Lifecycle + Logging.
 */
object AudioPlayback {

    private const val TAG = "AUDIO_PLAYBACK"

    private var running = false

    fun start() {
        if (running) {
            Log.w(TAG, "start(): already running")
            return
        }
        running = true
        Log.d(TAG, "AudioPlayback STARTED")

        // TODO:
        // - AudioTrack erzeugen
        // - Playback-Thread starten
    }

    fun play(pcm: ShortArray) {
        if (!running) {
            Log.w(TAG, "play(): called while playback is stopped")
            return
        }
        Log.d(TAG, "play(): received ${pcm.size} samples (FAKE playback)")
        // TODO:
        // - pcm-Daten in AudioTrack write()
    }

    fun stop() {
        if (!running) {
            Log.w(TAG, "stop(): already stopped")
            return
        }
        running = false
        Log.d(TAG, "AudioPlayback STOPPED")

        // TODO:
        // - AudioTrack.release()
        // - Threads stoppen
    }

    fun isRunning(): Boolean = running
}

    // PATCH 212 — Playback Thread
    private var thread: AudioPlaybackThread? = null

    override fun start() {
        if (running) {
            Log.w(TAG, "start(): already running")
            return
        }
        running = true
        thread = AudioPlaybackThread()
        thread?.start()
        Log.d(TAG, "AudioPlayback: thread started")
    }

    override fun play(pcm: ShortArray) {
        if (!running) {
            Log.w(TAG, "play(): ignoring, not running")
            return
        }
        thread?.offer(pcm)
    }

    override fun stop() {
        if (!running) {
            Log.w(TAG, "stop(): already stopped")
            return
        }
        running = false
        thread?.shutdown()
        thread = null
        Log.d(TAG, "AudioPlayback: thread stopped")
    }

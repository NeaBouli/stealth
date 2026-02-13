package com.securecall.app.ghostnet.media.audio

import android.util.Log

/**
 * PATCH 210 / 212:
 * AudioTrack Playback with thread support.
 */
object AudioPlayback {

    private const val TAG = "AUDIO_PLAYBACK"

    private var running = false
    private var thread: AudioPlaybackThread? = null

    fun start() {
        if (running) {
            Log.w(TAG, "start(): already running")
            return
        }
        running = true
        thread = AudioPlaybackThread()
        thread?.start()
        Log.d(TAG, "AudioPlayback: thread started")
    }

    fun play(pcm: ShortArray) {
        if (!running) {
            Log.w(TAG, "play(): ignoring, not running")
            return
        }
        thread?.offer(pcm)
    }

    fun stop() {
        if (!running) {
            Log.w(TAG, "stop(): already stopped")
            return
        }
        running = false
        thread?.shutdown()
        thread = null
        Log.d(TAG, "AudioPlayback: thread stopped")
    }

    fun isRunning(): Boolean = running
}

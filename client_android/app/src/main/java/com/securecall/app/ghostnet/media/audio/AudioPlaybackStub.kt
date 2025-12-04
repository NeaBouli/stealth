package com.securecall.app.ghostnet.media.audio

import android.util.Log

/**
 * AUDIO-10:
 * Stub for the future audio playback pipeline.
 *
 * Responsibilities (later):
 * - accept PCM samples (decoded from Opus),
 * - buffer them (optional jitter buffer),
 * - feed them into AudioTrack for low-latency playback.
 *
 * Current stub:
 * - logs incoming PCM buffers,
 * - does NOT touch AudioTrack yet,
 * - safe to keep in debug builds.
 */
object AudioPlaybackStub {

    private const val TAG = "AUDIO_PLAYBACK_STUB"

    /**
     * Feed PCM data into the playback pipeline (later).
     *
     * @param pcm     raw PCM samples (e.g. 16-bit, 48 kHz, mono)
     * @param frameId optional debug identifier (e.g. inbound frame counter)
     */
    fun enqueuePcm(pcm: ByteArray, frameId: Long? = null) {
        val id = frameId?.toString() ?: "n/a"
        Log.d(TAG, "enqueuePcm(): size=${pcm.size} bytes, frameId=$id (stub)")
        // TODO (later):
        // - push into a jitter/playback buffer
        // - wake up AudioTrack thread
    }

    /**
     * Called when a call starts.
     * Later this might initialise buffers/threads/AudioTrack.
     */
    fun onCallStart() {
        Log.d(TAG, "onCallStart(): init playback state (stub)")
    }

    /**
     * Called when a call ends.
     * Later this should stop and release all playback resources.
     */
    fun onCallEnd() {
        Log.d(TAG, "onCallEnd(): cleanup playback state (stub)")
    }
}

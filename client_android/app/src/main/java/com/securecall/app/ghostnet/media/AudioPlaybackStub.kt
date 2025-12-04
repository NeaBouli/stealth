package com.securecall.app.ghostnet.media

import android.util.Log

/**
 * AUDIO-10:
 * Minimal stub for audio playback pipeline.
 *
 * Responsibilities (later):
 * - receive decoded PCM frames,
 * - buffer them for playback,
 * - drive an AudioTrack (or similar) on a dedicated thread.
 *
 * For now it only logs the received PCM size so that
 * developers can verify the end-to-end flow without
 * shipping any real playback implementation.
 */
object AudioPlaybackStub {

    private const val TAG = "AUDIO_PLAYBACK"

    /**
     * Enqueue a PCM frame (16-bit, mono, sample rate TBD).
     *
     * In this stub implementation, we only log the size.
     * Later, this will push into a jitter buffer / playback queue.
     */
    fun enqueuePcm(pcm: ByteArray) {
        Log.d(TAG, "enqueuePcm(): got ${pcm.size} bytes of PCM (stub)")
    }
}

#!/bin/bash
set -e

echo "== patch_021: add AudioPlaybackStub =="

cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/media/AudioPlaybackStub.kt
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
KOT

echo "[OK] Created AudioPlaybackStub.kt"
echo "== patch_021 done =="

package com.securecall.app.ghostnet.media.playback

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * BACKEND-66:
 * Einfacher AudioTrack-Wrapper.
 * Später: saubere Lifecycle-Steuerung und eigenes Audio-Threading.
 */
object AudioPlayer {

    private const val TAG = "AUDIO_PLAYER"
    private const val SAMPLE_RATE = 8000  // Placeholder: 8 kHz

    @Volatile
    private var track: AudioTrack? = null

    @Synchronized
    private fun ensureTrack() {
        if (track != null) return

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuf <= 0) {
            Log.e(TAG, "getMinBufferSize() failed: $minBuf")
            return
        }

        val t = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf,
            AudioTrack.MODE_STREAM
        )

        if (t.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack not initialized, state=${t.state}")
            t.release()
            return
        }

        t.play()
        track = t
        Log.d(TAG, "AudioTrack created and started (rate=$SAMPLE_RATE, buf=$minBuf)")
    }

    fun play(samples: ShortArray) {
        ensureTrack()
        val t = track ?: run {
            Log.e(TAG, "play(): no AudioTrack available")
            return
        }

        if (samples.isEmpty()) {
            Log.w(TAG, "play(): empty PCM buffer")
            return
        }

        val written = t.write(samples, 0, samples.size)
        Log.d(TAG, "play(): wrote $written samples (req=${samples.size})")
    }

    @Synchronized
    fun stopAndRelease() {
        val t = track ?: return
        try {
            t.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stop() failed: ${e.message}")
        }
        t.release()
        track = null
        Log.d(TAG, "AudioTrack stopped and released")
    }
}

#!/bin/bash
set -e

echo "== patch_031: replace AudioPlaybackStub with AudioTrack-based implementation =="

cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/media/AudioPlaybackStub.kt
package com.securecall.app.ghostnet.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

/**
 * AUDIO-10:
 * Minimal AudioTrack-basierte Playback-Implementierung für eingehendes PCM.
 *
 * - Erwartet PCM 16-bit, mono.
 * - SAMPLE_RATE ist aktuell 48 kHz (an Encoder anpassen, wenn nötig).
 * - Wird über MediaRouterInboundStub.handleDecodedPcm() gefüttert.
 *
 * Dies ist bewusst einfach gehalten und soll nur zeigen:
 * "Audio kommt wirklich am Ohr an".
 */
object AudioPlaybackStub {

    private const val TAG = "AUDIO_PLAYBACK"

    // TODO: an die tatsächliche Pipeline anpassen (z.B. 16k / 32k / 48k)
    private const val SAMPLE_RATE_HZ = 48000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private val queue = LinkedBlockingQueue<ByteArray>()

    @Volatile
    private var running = false

    private var worker: Thread? = null

    /**
     * Von außen aufgerufen, um PCM-Frames in die Playback-Queue zu legen.
     */
    fun enqueuePcm(pcm: ByteArray) {
        queue.offer(pcm)
        if (!running) {
            start()
        }
    }

    fun start() {
        if (running) {
            return
        }
        running = true
        worker = Thread({ loop() }, "AudioPlaybackThread").apply { start() }
        Log.d(TAG, "Audio playback thread started")
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        Log.d(TAG, "Audio playback thread stopped")
    }

    private fun loop() {
        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufSize <= 0) {
            Log.e(TAG, "Invalid min buffer size: $minBufSize")
            running = false
            return
        }

        val bufferSize = minBufSize.coerceAtLeast(2048)

        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        try {
            audioTrack.play()
            Log.d(TAG, "AudioTrack started (bufferSize=$bufferSize)")

            while (running) {
                try {
                    val pcm = queue.poll()
                    if (pcm != null) {
                        val written = audioTrack.write(pcm, 0, pcm.size)
                        Log.d(TAG, "wrote $written bytes to AudioTrack (pcm=${pcm.size})")
                    } else {
                        Thread.sleep(5)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Playback loop error", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start AudioTrack", t)
        } finally {
            try {
                audioTrack.stop()
            } catch (_: Throwable) {
            }
            try {
                audioTrack.release()
            } catch (_: Throwable) {
            }
            Log.d(TAG, "AudioTrack released")
        }
    }
}
KOT

echo "[OK] Replaced AudioPlaybackStub.kt with AudioTrack-based implementation"
echo "== patch_031 done =="

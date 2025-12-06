#!/bin/bash
set -e

echo "== patch_038: restore ghostnet media Kotlin stubs =="

# Sicherstellen, dass das Package-Verzeichnis existiert
mkdir -p client_android/app/src/main/java/com/securecall/app/ghostnet/media

# 1) AudioPlaybackStub.kt – AudioTrack-basierte, einfache Playback-Queue
cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/media/AudioPlaybackStub.kt
package com.securecall.app.ghostnet.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

/**
 * Simple inbound PCM playback using AudioTrack.
 *
 * Exposed via enqueuePcm(), used by MediaRouterInboundStub.
 */
object AudioPlaybackStub {

    private const val TAG = "AUDIO_PLAYBACK_STUB"

    private val queue = LinkedBlockingQueue<ByteArray>(32)

    @Volatile
    private var worker: Thread? = null

    /**
     * Called from Java/Kotlin to push decoded PCM into playback.
     */
    @JvmStatic
    fun enqueuePcm(pcm: ByteArray) {
        if (!queue.offer(pcm)) {
            Log.w(TAG, "PCM queue full, dropping frame of size=\${pcm.size}")
        }
        ensureWorker()
    }

    @Synchronized
    private fun ensureWorker() {
        if (worker != null) return

        val t = Thread(
            {
                Log.d(TAG, "Audio worker started")
                val sampleRate = 48000
                val channelConfig = AudioFormat.CHANNEL_OUT_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT

                val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                if (minBuf <= 0) {
                    Log.e(TAG, "Invalid min buffer size: \$minBuf")
                    return@Thread
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(audioFormat)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                try {
                    track.play()
                    Log.d(TAG, "AudioTrack started (sr=\$sampleRate, buf=\${minBuf * 2})")

                    while (!Thread.currentThread().isInterrupted) {
                        val pcm = queue.take()  // block until data
                        val written = track.write(pcm, 0, pcm.size)
                        Log.d(TAG, "wrote \$written bytes to AudioTrack (pcm=\${pcm.size})")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Playback loop error", t)
                } finally {
                    try {
                        track.stop()
                    } catch (_: Throwable) {
                    }
                    try {
                        track.release()
                    } catch (_: Throwable) {
                    }
                    Log.d(TAG, "AudioTrack released")
                }
            },
            "AudioPlaybackStubWorker"
        )

        t.isDaemon = true
        t.start()
        worker = t
    }
}
KOT

# 2) MediaRouterInboundStub.kt – Einstiegspunkt für decoded PCM
cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/media/MediaRouterInboundStub.kt
package com.securecall.app.ghostnet.media

import android.util.Log

/**
 * Minimal inbound media router:
 * - receives decoded PCM frames,
 * - forwards them to AudioPlaybackStub.
 */
object MediaRouterInboundStub {

    private const val TAG = "MEDIA_ROUTER_INBOUND"

    @JvmStatic
    fun handleDecodedPcm(pcm: ByteArray) {
        Log.d(TAG, "handleDecodedPcm(): got \${pcm.size} bytes of PCM, forwarding to AudioPlaybackStub")
        AudioPlaybackStub.enqueuePcm(pcm)
    }
}
KOT

echo "[OK] restored Kotlin media stubs (AudioPlaybackStub + MediaRouterInboundStub)"
echo "== patch_038 done =="

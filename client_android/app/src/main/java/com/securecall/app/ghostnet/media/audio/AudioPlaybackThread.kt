package com.securecall.app.ghostnet.media.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

/**
 * PATCH 212:
 * AudioTrack Playback Thread (REAL skeleton)
 *
 * Dieser Thread nimmt PCM-Daten entgegen und schreibt sie in AudioTrack.
 */
class AudioPlaybackThread : Thread("AudioPlaybackThread") {

    private val TAG = "AUDIO_PLAYBACK_T"
    private var running = true

    private val queue = LinkedBlockingQueue<ShortArray>()

    private val sampleRate = 48000
    private val channel = AudioFormat.CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    private val minBuffer = AudioTrack.getMinBufferSize(
        sampleRate,
        channel,
        encoding
    )

    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channel)
                .setEncoding(encoding)
                .build()
        )
        .setBufferSizeInBytes(minBuffer)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    override fun run() {
        Log.d(TAG, "Playback thread STARTED")
        track.play()

        while (running) {
            try {
                val pcm = queue.take()  // blockiert bis Daten kommen
                val bytes = shortArrayToByteArray(pcm)
                track.write(bytes, 0, bytes.size)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in playback loop", t)
            }
        }

        Log.d(TAG, "Playback thread stopping…")
        try {
            track.stop()
            track.release()
        } catch (t: Throwable) {
            Log.e(TAG, "Release failed", t)
        }
        Log.d(TAG, "Playback thread STOPPED")
    }

    fun offer(pcm: ShortArray) {
        queue.offer(pcm)
    }

    fun shutdown() {
        running = false
        interrupt()
    }

    private fun shortArrayToByteArray(arr: ShortArray): ByteArray {
        val out = ByteArray(arr.size * 2)
        var j = 0
        for (s in arr) {
            out[j] = (s.toInt() and 0xFF).toByte()
            out[j + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
            j += 2
        }
        return out
    }
}

package com.securecall.app.ghostnet.media.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * PATCH 226: Skeleton-Audio-Player.
 * Später: echte PCM-Daten aus Decoder an AudioTrack übergeben.
 */
class GhostAudioPlayer(
    private val sampleRate: Int = 48000,
    private val channels: Int = 1
) {

    private var audioTrack: AudioTrack? = null
    private var isPrepared = false
    private var isPlaying = false

    fun prepare() {
        if (isPrepared) return

        Log.d("AUDIO_PLAYER", "prepare(): initializing AudioTrack")

        val channelConfig = if (channels == 1)
            AudioFormat.CHANNEL_OUT_MONO
        else
            AudioFormat.CHANNEL_OUT_STEREO

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Fix Bug #2 (2026-04-18): use AudioTrack.Builder with AudioAttributes
        // instead of the deprecated stream-type constructor. The deprecated
        // constructor fixes the routing at creation time on Samsung Android 12+,
        // causing setSpeakerphoneOn() to be ignored during an active call.
        // AudioAttributes(USAGE_VOICE_COMMUNICATION) correctly follows the
        // AudioManager routing decisions at runtime.
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        isPrepared = true
        Log.d("AUDIO_PLAYER", "prepare(): done, buffer=$minBuf")
    }

    fun play() {
        if (!isPrepared) prepare()
        if (isPlaying) return

        audioTrack?.play()
        isPlaying = true
        Log.d("AUDIO_PLAYER", "play(): started")
    }

    fun write(pcm: ShortArray) {
        if (!isPrepared) prepare()
        if (!isPlaying) play()

        val track = audioTrack ?: return

        val wrote = track.write(pcm, 0, pcm.size)
        Log.d("AUDIO_PLAYER", "write(): wrote=$wrote samples")
    }

    fun stop() {
        if (!isPrepared) return
        audioTrack?.stop()
        isPlaying = false
        Log.d("AUDIO_PLAYER", "stop()")
    }

    fun release() {
        audioTrack?.release()
        audioTrack = null
        isPrepared = false
        isPlaying = false
        Log.d("AUDIO_PLAYER", "release()")
    }
}

package com.securecall.app.ghostnet.media.playback

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

        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
            AudioTrack.MODE_STREAM
        )

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

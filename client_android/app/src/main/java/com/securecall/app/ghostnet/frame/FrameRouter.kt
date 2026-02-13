package com.securecall.app.ghostnet.frame

import android.util.Log

/**
 * CRYPTO-25:
 * Einfacher FrameRouter-Stub.
 */
object FrameRouter {

    private const val TAG = "FRAME_ROUTER"

    fun route(mediaData: ByteArray) {
        if (mediaData.isEmpty()) {
            Log.w(TAG, "route(): empty payload")
            return
        }
        val type = mediaData[0].toInt() and 0xFF
        when (type) {
            0x01 -> handleAudio(mediaData)
            0x02 -> handleControl(mediaData)
            0x03 -> handleKeepAlive(mediaData)
            else -> Log.w(TAG, "route(): unknown frame type=0x${type.toString(16)}, size=${mediaData.size}")
        }
    }

    private fun handleAudio(mediaData: ByteArray) {
        Log.d(TAG, "handleAudio(): size=${mediaData.size}")
    }

    private fun handleControl(mediaData: ByteArray) {
        Log.d(TAG, "handleControl(): size=${mediaData.size}")
    }

    private fun handleKeepAlive(mediaData: ByteArray) {
        Log.d(TAG, "handleKeepAlive(): size=${mediaData.size}")
    }

    // CRYPTO-26: Structured Route Entry
    fun routeStructured(parsed: Any?) {
        if (parsed == null) {
            Log.w(TAG, "routeStructured(): parsed=null, skipping")
            return
        }
        when (parsed) {
            is AudioFrame -> {
                Log.d(TAG, "StructuredRoute: AudioFrame ts=${parsed.timestamp} size=${parsed.data.size}")
                handleAudioFrame(parsed)
            }
            is ControlFrame -> {
                Log.d(TAG, "StructuredRoute: ControlFrame code=${parsed.code} info=${parsed.info}")
                handleControlFrame(parsed)
            }
            is KeepAliveFrame -> {
                Log.d(TAG, "StructuredRoute: KeepAliveFrame ts=${parsed.timestamp}")
                handleKeepAliveFrame(parsed)
            }
            else -> Log.w(TAG, "StructuredRoute: Unknown object=$parsed")
        }
    }

    private fun handleAudioFrame(frame: AudioFrame) {
        Log.d(TAG, "AudioFrame → Audio pipeline TBD, data=${frame.data.size}")
    }

    private fun handleControlFrame(frame: ControlFrame) {
        Log.d(TAG, "ControlFrame: code=${frame.code} info=${frame.info}")
    }

    private fun handleKeepAliveFrame(frame: KeepAliveFrame) {
        Log.d(TAG, "KeepAlive received")
    }
}

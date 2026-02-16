package com.securecall.app.ghostnet.media.codec

import android.util.Log
import com.securecall.app.ghostnet.media.native.NativeOpus

/**
 * Opus encoder wrapper. Encodes PCM ShortArray → Opus ByteArray.
 * Frame size: 960 samples (20ms at 48kHz mono).
 */
object OpusEncoder {

    private const val TAG = "OPUS_ENCODER"
    const val FRAME_SIZE = 960 // 20ms at 48kHz

    private var handle: Long = -1

    fun init(sampleRate: Int = 48000, channels: Int = 1) {
        if (handle > 0) {
            Log.w(TAG, "init(): already initialised, releasing first")
            release()
        }
        handle = NativeOpus.nativeInit(sampleRate, channels)
        if (handle < 0) {
            Log.e(TAG, "init(): nativeInit failed")
        } else {
            Log.d(TAG, "init(): handle=$handle (sr=$sampleRate, ch=$channels)")
        }
    }

    fun encode(pcm: ShortArray): ByteArray {
        if (handle <= 0) {
            Log.e(TAG, "encode(): not initialised")
            return ByteArray(0)
        }
        return NativeOpus.nativeEncode(handle, pcm)
    }

    fun release() {
        if (handle > 0) {
            NativeOpus.nativeRelease(handle)
            Log.d(TAG, "release(): handle=$handle destroyed")
            handle = -1
        }
    }

    fun isInitialised(): Boolean = handle > 0
}

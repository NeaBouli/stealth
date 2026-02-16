package com.securecall.app.ghostnet.media.codec

import android.util.Log
import com.securecall.app.ghostnet.media.native.NativeOpus

/**
 * Opus decoder wrapper. Decodes Opus ByteArray → PCM ShortArray.
 */
object OpusDecoder {

    private const val TAG = "OPUS_DECODER"

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

    fun decode(encoded: ByteArray): ShortArray {
        if (handle <= 0) {
            Log.e(TAG, "decode(): not initialised")
            return ShortArray(0)
        }
        if (encoded.isEmpty()) {
            Log.w(TAG, "decode(): empty input")
            return ShortArray(0)
        }
        return NativeOpus.nativeDecode(handle, encoded)
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

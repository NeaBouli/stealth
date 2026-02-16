package com.securecall.app.ghostnet.media.native

import android.util.Log

/**
 * JNI bindings for libopus encoder/decoder via native-lib.cpp.
 */
object NativeOpus {

    private const val TAG = "NATIVE_OPUS"

    init {
        try {
            System.loadLibrary("securecall")
            Log.d(TAG, "Native library loaded")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load native library", t)
        }
    }

    external fun nativeInit(sampleRate: Int, channels: Int): Long
    external fun nativeEncode(handle: Long, pcm: ShortArray): ByteArray
    external fun nativeDecode(handle: Long, data: ByteArray): ShortArray
    external fun nativeRelease(handle: Long)
}

package com.securecall.app.audio.jitter

import android.util.Log

// BACKEND-48 / ANDROID-03:
// JitterBuffer — buffers decoded PCM frames and releases at steady rate.
// Pre-buffers PREFILL frames before playback starts to absorb network jitter.
object JitterBuffer {

    private val TAG = "JITTER_BUFFER"
    private val buffer: java.util.LinkedList<ShortArray> = java.util.LinkedList()

    private const val MAX_SIZE = 32
    const val PREFILL = 3  // 3 frames × 20ms = 60ms pre-buffer

    @Synchronized
    fun push(frame: ShortArray) {
        if (buffer.size >= MAX_SIZE) {
            buffer.removeFirst()
            Log.w(TAG, "Overflow — dropped oldest frame")
        }
        buffer.addLast(frame)
    }

    @Synchronized
    fun pop(): ShortArray? {
        return if (buffer.isEmpty()) null else buffer.removeFirst()
    }

    @Synchronized
    fun size(): Int = buffer.size

    @Synchronized
    fun clear() {
        buffer.clear()
        Log.d(TAG, "Buffer cleared")
    }
}

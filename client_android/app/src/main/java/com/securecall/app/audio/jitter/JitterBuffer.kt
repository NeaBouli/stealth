package com.securecall.app.audio.jitter

import android.util.Log
import java.util.LinkedList

// BACKEND-48 / ANDROID-03:
// Platzhalter-JitterBuffer (FIFO + Debug)
// Später: Zeitstempel, Latenz-Kompensation, Packet Reordering.
object JitterBuffer {

    private val TAG = "JITTER_BUFFER"
    private val buffer: LinkedList<ByteArray> = LinkedList()

    private const val MAX_SIZE = 32  // Placeholder-Kapazität

    @Synchronized
    fun push(frame: ByteArray) {
        if (buffer.size >= MAX_SIZE) {
            buffer.removeFirst() // ältestes Frame verwerfen
            Log.w(TAG, "Buffer overflow → dropped oldest frame")
        }
        buffer.addLast(frame)
        Log.d(TAG, "push(): size=${buffer.size}")
    }

    @Synchronized
    fun pop(): ByteArray? {
        if (buffer.isEmpty()) return null
        val out = buffer.removeFirst()
        Log.d(TAG, "pop(): delivered frame, remaining=${buffer.size}")
        return out
    }

    fun size(): Int = buffer.size
}

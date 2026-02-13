package com.securecall.app.ghostnet.transport.queue

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

// BACKEND-34 / ANDROID-03:
// Placeholder für AudioFrame-Queue
class TransportFrameQueue {

    companion object {
        private var instance: TransportFrameQueue? = null

        fun get(): TransportFrameQueue {
            if (instance == null) {
                instance = TransportFrameQueue()
            }
            return instance!!
        }
    }

    private val queue = ConcurrentLinkedQueue<ByteArray>()

    fun enqueue(frame: ByteArray) {
        queue.add(frame)
        Log.d("FRAME_QUEUE", "Frame enqueued (size=${frame.size}) queue=${queue.size}")
    }

    fun dequeue(): ByteArray? {
        val f = queue.poll()
        if (f != null) {
            Log.d("FRAME_QUEUE", "Frame dequeued, remaining=${queue.size}")
        }
        return f
    }

    fun size(): Int = queue.size

    fun clear() {
        queue.clear()
        Log.d("FRAME_QUEUE", "Queue cleared")
    }

    // BACKEND-38: Overload für AudioFrame
    fun enqueue(frame: com.securecall.app.ghostnet.transport.frame.AudioFrame) {
        queue.add(frame.data)
        Log.d("FRAME_QUEUE", "AudioFrame enqueued (type=${frame.type}) size=${frame.data.size}")
    }

    // BACKEND-39: Builder-Frames enqueuen
    fun enqueueFrame(frame: com.securecall.app.ghostnet.transport.frame.AudioFrame) {
        queue.add(frame.data)
        Log.d("FRAME_QUEUE", "enqueueFrame(): ${frame}")
    }
}

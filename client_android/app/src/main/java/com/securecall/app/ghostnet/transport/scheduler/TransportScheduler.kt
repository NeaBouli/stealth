package com.securecall.app.ghostnet.transport.scheduler

import android.util.Log
import java.util.Timer
import java.util.TimerTask

// BACKEND-37 / ANDROID-03:
// TransportScheduler — pumpt regelmäßig Frames aus der Queue in den Transport-Thread
class TransportScheduler {

    private var timer: Timer? = null
    private val intervalMs: Long = 10 // 100Hz

    private val queue = com.securecall.app.ghostnet.transport.queue.TransportFrameQueue.get()

    fun start() {
        if (timer != null) return

        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                val frame = queue.dequeue()
                if (frame != null) {
                    Log.d("GHOST_TSCHED", "Scheduler pumping frame size=${frame.size}")
                    // später: Übergabe an TransportThread
                }
            }
        }, intervalMs, intervalMs)

        Log.d("GHOST_TSCHED", "TransportScheduler started")
    }

    fun stop() {
        timer?.cancel()
        timer = null
        Log.d("GHOST_TSCHED", "TransportScheduler stopped")
    }
}

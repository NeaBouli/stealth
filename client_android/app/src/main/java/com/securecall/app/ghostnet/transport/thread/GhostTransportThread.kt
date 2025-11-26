package com.securecall.app.ghostnet.transport.thread

import android.util.Log
import com.securecall.app.ghostnet.transport.queue.TransportFrameQueue

// BACKEND-36 / ANDROID-03:
// TransportThread — verarbeitet Frames aus der Queue kontinuierlich
class GhostTransportThread : Thread("GhostTransportThread") {

    @Volatile
    private var running = true

    private val queue = TransportFrameQueue.get()
    private val TAG = "GHOST_TTHREAD"

    override fun run() {
        Log.d(TAG, "TransportThread is running")

        while (running) {
            try {
                // Frame holen
                val frame = queue.dequeue()

                if (frame != null) {
                    Log.d(TAG, "Dequeued frame: size=${frame.size}")
                    // später: QUIC / SRTP / Routing / Multi-Hop hier integrieren
                }

                // sehr leichter Sleep, verhindert Busy-Loop
                sleep(5)

            } catch (e: InterruptedException) {
                Log.d(TAG, "Thread interruption received")
                running = false
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error in transport loop", t)
            }
        }

        Log.d(TAG, "TransportThread stopped")
    }

    fun stopThread() {
        running = false
        interrupt()
        Log.d(TAG, "stopThread() called")
    }
}

            // BACKEND-43: Frame an Debug-Logger weitergeben
            com.securecall.app.ghostnet.transport.debug.IncomingFrameLogger.logFrame(frame)

            // BACKEND-44: Frame Routing
            com.securecall.app.ghostnet.transport.router.FrameRouter.route(frame)

                // BACKEND-61: Frame an zentralen Router weitergeben
                com.securecall.app.ghostnet.control.GhostControlRouter.routeIncoming(frame)

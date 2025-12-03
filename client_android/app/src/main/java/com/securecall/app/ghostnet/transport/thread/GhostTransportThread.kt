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

    // PATCH 228: adaptive sleep based on queue load
    private fun adaptiveSleep(queueSize: Int) {
        when {
            queueSize > 20 -> sleep(1)    // hohe Last → häufiger arbeiten
            queueSize > 5  -> sleep(3)    // moderate Last
            else           -> sleep(6)    // geringe Last → CPU schonen
        }
    }

    // PATCH 228: main load handler
    private fun handleThreadLoad() {
        val q = queue.size()
        android.util.Log.d("GHOST_TTHREAD", "load: queue=$q")
        adaptiveSleep(q)
    }

    // PATCH 228: inside run() loop
    handleThreadLoad()

    // PATCH 230: call becomes active on first media-frame
    private var callActivated = false

            // PATCH 230: detect first frame → call active
            if (!callActivated && frame != null) {
                callActivated = true
                android.util.Log.d("GHOST_CALL", "TransportThread: first frame → call active")
                com.securecall.app.ghostnet.call.GhostCallController.markCallActive()
            }

                // PATCH 230: soft call termination hint
                android.util.Log.e("GHOST_CALL", "transport error → soft-terminate", t)
                com.securecall.app.ghostnet.call.GhostCallController.terminateCall()

    // PATCH 231 — graceful shutdown
    fun gracefulStop() {
        running = false
        interrupt()
        android.util.Log.d("GHOST_TTHREAD", "gracefulStop(): stopping transport thread")
    }

    fun queueSize(): Int = queue.size()

    // PATCH 240: EventBus-Helfer
    private fun postDequeuedEvent(size: Int) {
        com.securecall.app.debug.GhostDebugEventBus.post(
            "TRANS_THR",
            "Dequeued frame size=$size"
        )
    }

                if (frame != null) {
                    postDequeuedEvent(frame.size)
                }

    // CRYPTO-30: encrypted-frame dequeue
    private fun pollEncrypted(): com.securecall.app.ghostnet.transport.EncryptedFrame? {
        return com.securecall.app.ghostnet.transport.GhostTransport.get().dequeueEncryptedFrame()
    }

            // CRYPTO-30: encrypted frames abholen
            val enc = pollEncrypted()
            if (enc != null) {
                android.util.Log.d("GHOST_TRANSPORT", "TransportThread: encrypted frame dequeued size=${enc.data.size}")
                // TODO: später an Network-Sendepipeline geben
            }

            // CRYPTO-32: encrypted → network-sender
            if (enc != null) {
                com.securecall.app.ghostnet.transport.GhostTransport.get()
                    .forwardEncryptedToNetwork(enc)
            }

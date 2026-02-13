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

    // PATCH 230: call becomes active on first media-frame
    private var callActivated = false

    override fun run() {
        Log.d(TAG, "TransportThread is running")

        while (running) {
            try {
                // Frame holen
                val frame = queue.dequeue()

                if (frame != null) {
                    Log.d(TAG, "Dequeued frame: size=${frame.size}")

                    // BACKEND-43: Frame an Debug-Logger weitergeben
                    try {
                        com.securecall.app.ghostnet.transport.debug.IncomingFrameLogger.logFrame(frame)
                    } catch (_: Throwable) {}

                    // BACKEND-44: Frame Routing
                    com.securecall.app.ghostnet.transport.router.FrameRouter.route(frame)

                    // BACKEND-61: Frame an zentralen Router weitergeben
                    com.securecall.app.ghostnet.control.GhostControlRouter.routeIncoming(frame)

                    // PATCH 240: EventBus
                    postDequeuedEvent(frame.size)

                    // PATCH 230: detect first frame → call active
                    if (!callActivated) {
                        callActivated = true
                        Log.d("GHOST_CALL", "TransportThread: first frame → call active")
                        com.securecall.app.ghostnet.call.GhostCallController.markCallActive()
                    }
                }

                // CRYPTO-30: encrypted frames abholen
                val enc = pollEncrypted()
                if (enc != null) {
                    Log.d("GHOST_TRANSPORT", "TransportThread: encrypted frame dequeued size=${enc.data.size}")
                    // CRYPTO-32: encrypted → network-sender
                    com.securecall.app.ghostnet.transport.GhostTransport.forwardEncryptedToNetwork(enc)
                }

                // PATCH 228: adaptive sleep based on queue load
                handleThreadLoad()

            } catch (e: InterruptedException) {
                Log.d(TAG, "Thread interruption received")
                running = false
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error in transport loop", t)
                // PATCH 230: soft call termination hint
                Log.e("GHOST_CALL", "transport error → soft-terminate", t)
                com.securecall.app.ghostnet.call.GhostCallController.terminateCall()
            }
        }

        Log.d(TAG, "TransportThread stopped")
    }

    fun stopThread() {
        running = false
        interrupt()
        Log.d(TAG, "stopThread() called")
    }

    // PATCH 231: graceful shutdown
    fun gracefulStop() {
        running = false
        interrupt()
        Log.d(TAG, "gracefulStop(): stopping transport thread")
    }

    fun queueSize(): Int = queue.size()

    // PATCH 228: adaptive sleep based on queue load
    private fun adaptiveSleep(queueSize: Int) {
        when {
            queueSize > 20 -> sleep(1)
            queueSize > 5  -> sleep(3)
            else           -> sleep(6)
        }
    }

    private fun handleThreadLoad() {
        val q = queue.size()
        adaptiveSleep(q)
    }

    // PATCH 240: EventBus-Helfer
    private fun postDequeuedEvent(size: Int) {
        com.securecall.app.debug.GhostDebugEventBus.post(
            "TRANS_THR",
            "Dequeued frame size=$size"
        )
    }

    // CRYPTO-30: encrypted-frame dequeue
    private fun pollEncrypted(): com.securecall.app.ghostnet.transport.EncryptedFrame? {
        return com.securecall.app.ghostnet.transport.GhostTransport.dequeueEncryptedFrame()
    }
}

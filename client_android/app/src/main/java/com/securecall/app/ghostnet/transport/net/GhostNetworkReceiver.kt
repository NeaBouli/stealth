package com.securecall.app.ghostnet.transport.net

import android.util.Log
import com.securecall.app.ghostnet.transport.EncryptedFrame
import java.util.concurrent.LinkedBlockingQueue

/**
 * CRYPTO-33:
 * Stub-Empfänger für eingehende Netzwerkframes.
 *
 * Später: WebSocket, QUIC, SRTP etc.
 */
object GhostNetworkReceiver {

    private const val TAG = "GHOST_NET_RECV"

    private val queue = LinkedBlockingQueue<EncryptedFrame>()
    @Volatile
    private var running = false
    private var worker: Thread? = null

    fun injectIncomingDummy(data: ByteArray) {
        queue.offer(EncryptedFrame(data))
        Log.d(TAG, "injectIncomingDummy(): size=${data.size}")
    }

    fun poll(): EncryptedFrame? {
        return queue.poll()
    }

    fun start() {
        if (running) return
        running = true

        worker = Thread {
            Log.d(TAG, "NetworkReceiver thread started")
            while (running) {
                try {
                    Thread.sleep(5)
                } catch (_: Throwable) {}
            }
            Log.d(TAG, "NetworkReceiver thread stopped")
        }
        worker!!.start()
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }
}

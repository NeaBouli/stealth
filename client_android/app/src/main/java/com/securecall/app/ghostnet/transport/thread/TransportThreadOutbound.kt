package com.securecall.app.ghostnet.transport.thread

import android.util.Log
import com.securecall.app.ghostnet.transport.net.GhostNetworkSender

/**
 * CRYPTO-40:
 * Outbound-Sender-Thread: nimmt Frames aus der Queue und sendet sie
 * an die Netzwerk-Schicht.
 */
class TransportThreadOutbound(
    private val sender: GhostNetworkSender
) : Thread("OutboundThread") {

    @Volatile
    private var running = true

    override fun run() {
        Log.d("OUTBOUND", "TransportThreadOutbound RUN")
        while (running) {
            try {
                val frame = sender.dequeueOutbound()
                if (frame != null) {
                    sender.sendRawNetworkFrame(frame)
                }
                sleep(2) // minimale Drosselung
            } catch (t: Throwable) {
                Log.e("OUTBOUND", "Outbound error", t)
            }
        }
        Log.d("OUTBOUND", "TransportThreadOutbound STOP")
    }

    fun stopThread() {
        running = false
        interrupt()
    }
}

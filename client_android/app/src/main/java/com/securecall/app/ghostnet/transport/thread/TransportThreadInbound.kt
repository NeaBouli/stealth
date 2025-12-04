package com.securecall.app.ghostnet.transport.thread

import android.util.Log
import com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver

/**
 * CRYPTO-40 / NET-10:
 * Inbound transport thread (stub).
 *
 * This will later:
 * - read raw frames from the network layer,
 * - hand them to the frame parser,
 * - dispatch decoded frames to the media router.
 *
 * For now it only provides a compilable skeleton
 * with a real poll() hook into GhostNetworkReceiver.
 */
class TransportThreadInbound : Thread("InboundThread") {

    @Volatile
    private var running = true

    override fun run() {
        Log.d("INBOUND", "TransportThreadInbound RUN (stub)")
        while (running) {
            try {
                val raw = GhostNetworkReceiver.pollInboundFrame()
                if (raw != null) {
                    Log.d("INBOUND", "got inbound raw frame size=${raw.size}")
                    // TODO:
                    // 1) parse header + body (FrameHeaderUtils + FrameBodyParser)
                    // 2) dispatch to GhostMediaRouter
                }
                sleep(10)
            } catch (t: Throwable) {
                Log.e("INBOUND", "Inbound stub error", t)
            }
        }
        Log.d("INBOUND", "TransportThreadInbound STOP (stub)")
    }

    fun stopThread() {
        running = false
        interrupt()
    }
}

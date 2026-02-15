package com.securecall.app.ghostnet.transport.thread

import android.util.Log
import com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver
import com.securecall.app.ghostnet.frame.FrameParserStub

/**
 * CRYPTO-40 / NET-10:
 * Inbound transport thread (stub).
 *
 * Current responsibilities:
 *  - poll raw frames from GhostNetworkReceiver,
 *  - parse header + payload via FrameParserStub,
 *  - forward payload to MediaRouterInboundStub as decoded PCM (stub).
 *
 * Later this will:
 *  - use the real FrameHeaderV1 parser,
 *  - distinguish frame types,
 *  - dispatch audio/control to GhostMediaRouter.
 */
class TransportThreadInbound : Thread("InboundThread") {

    @Volatile
    private var running = true

    override fun run() {
        Log.d("INBOUND", "TransportThreadInbound RUN (stub)")
        while (running) {
            try {
                val frame = GhostNetworkReceiver.poll()
                if (frame != null) {
                    val raw = frame.data
                    Log.d("INBOUND", "got inbound raw frame size=${raw.size}")

                    val parsed = FrameParserStub.parse(raw)
                    if (parsed != null) {
                        Log.d(
                            "INBOUND",
                            "parsed frame v=${parsed.version} typeByte=${parsed.typeByte} payload=${parsed.payloadSize}"
                        )
                        // FrameParserStub.parse() already dispatches audio to MediaRouterInboundStub
                    }

                    // TODO (later):
                    // 1) replace FrameParserStub with real header/body parsing
                    // 2) route by type (AUDIO / CONTROL / KEEPALIVE)
                    // 3) dispatch to full GhostMediaRouter instead of stub
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

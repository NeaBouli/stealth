#!/bin/bash
set -e

echo "== patch_023: bridge inbound parser to media router stubs =="

# 1) FrameParserStub: parses version, typeByte, payload
cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/frame/FrameParserStub.kt
package com.securecall.app.ghostnet.frame

import android.util.Log

/**
 * CRYPTO-41 / INBOUND-STUB:
 * Very small stub parser for inbound frames.
 *
 * Layout (stub):
 *  - byte 0: version
 *  - byte 1: typeByte
 *  - bytes 2..N-1: payload (treated as PCM for now)
 *
 * This is intentionally simple and will be replaced once
 * FrameHeaderV1 + full parsing is implemented.
 */
data class ParsedFrameStub(
    val version: Int,
    val typeByte: Int,
    val payload: ByteArray
) {
    val payloadSize: Int get() = payload.size
}

object FrameParserStub {

    private const val TAG = "FRAME_PARSER_STUB"

    fun parse(raw: ByteArray): ParsedFrameStub? {
        if (raw.size < 2) {
            Log.w(TAG, "parse(): frame too short size=${raw.size}")
            return null
        }

        val version = raw[0].toInt() and 0xFF
        val typeByte = raw[1].toInt() and 0xFF
        val payload = raw.copyOfRange(2, raw.size)

        Log.d(TAG, "parse(): v=$version typeByte=$typeByte payloadSize=${payload.size}")

        return ParsedFrameStub(
            version = version,
            typeByte = typeByte,
            payload = payload
        )
    }
}
KOT

echo "[OK] (re)created FrameParserStub.kt"

# 2) TransportThreadInbound: use parser + forward to MediaRouterInboundStub
cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/transport/thread/TransportThreadInbound.kt
package com.securecall.app.ghostnet.transport.thread

import android.util.Log
import com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver
import com.securecall.app.ghostnet.frame.FrameParserStub
import com.securecall.app.ghostnet.media.MediaRouterInboundStub

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
                val raw = GhostNetworkReceiver.pollInboundFrame()
                if (raw != null) {
                    Log.d("INBOUND", "got inbound raw frame size=${raw.size}")

                    val parsed = FrameParserStub.parse(raw)
                    if (parsed != null) {
                        Log.d(
                            "INBOUND",
                            "parsed frame v=${parsed.version} typeByte=${parsed.typeByte} payload=${parsed.payloadSize}"
                        )

                        // For now: treat any parsed payload as PCM and forward
                        MediaRouterInboundStub.handleDecodedPcm(parsed.payload)
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
KOT

echo "[OK] Updated TransportThreadInbound.kt to forward PCM to MediaRouterInboundStub"

echo "== patch_023 done =="

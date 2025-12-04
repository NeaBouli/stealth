#!/bin/bash
set -e

echo "== patch_017: add inbound frame parser stub =="

# 1) Stub-Parser: liest nur Version, Typ-Byte, Payload-Länge aus
cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/frame/FrameParserStub.kt
package com.securecall.app.ghostnet.frame

import android.util.Log

/**
 * CRYPTO-41:
 * Temporary inbound frame parser stub.
 *
 * This does NOT implement the final FrameHeaderV1 format.
 * It only:
 *  - reads the first byte as "version",
 *  - reads the second byte as "typeByte",
 *  - treats the rest as payload.
 *
 * Later this will be replaced by proper parsing using FrameHeaderUtils + FrameBodyParser.
 */
data class ParsedFrameStub(
    val version: Int,
    val typeByte: Int,
    val payloadSize: Int
)

object FrameParserStub {

    private const val TAG = "FRAME_PARSER_STUB"

    fun parse(raw: ByteArray): ParsedFrameStub? {
        if (raw.size < 2) {
            Log.w(TAG, "parse(): raw too short size=${raw.size}")
            return null
        }

        val version = raw[0].toInt() and 0xFF
        val type = raw[1].toInt() and 0xFF
        val payloadSize = raw.size - 2

        Log.d(TAG, "parse(): v=$version type=$type payloadSize=$payloadSize")

        return ParsedFrameStub(
            version = version,
            typeByte = type,
            payloadSize = payloadSize
        )
    }
}
KOT
echo "[OK] Created FrameParserStub.kt"

# 2) TransportThreadInbound: nutzt jetzt den Stub-Parser
cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/transport/thread/TransportThreadInbound.kt
package com.securecall.app.ghostnet.transport.thread

import android.util.Log
import com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver
import com.securecall.app.ghostnet.frame.FrameParserStub

/**
 * CRYPTO-40 / NET-10:
 * Inbound transport thread (stub).
 *
 * This will later:
 * - read raw frames from the network layer,
 * - hand them to the real frame parser (FrameHeaderUtils + FrameBodyParser),
 * - dispatch decoded frames to the media router.
 *
 * For now it:
 * - polls GhostNetworkReceiver for raw frames,
 * - runs them through a minimal FrameParserStub,
 * - logs the parsed metadata.
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
                    }

                    // TODO (later):
                    // 1) replace FrameParserStub with real header/body parsing
                    // 2) dispatch parsed payload to GhostMediaRouter
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
echo "[OK] Updated TransportThreadInbound.kt"

echo "== patch_017 done =="

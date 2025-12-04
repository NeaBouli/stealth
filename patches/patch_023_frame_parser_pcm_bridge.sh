#!/bin/bash
set -e

echo "== patch_023: bridge inbound frame parser to media router stub =="

cat <<'KOT' > client_android/app/src/main/java/com/securecall/app/ghostnet/frame/FrameParserStub.kt
package com.securecall.app.ghostnet.frame

import android.util.Log
import com.securecall.app.ghostnet.media.MediaRouterInboundStub

/**
 * CRYPTO-41:
 * Simple inbound frame parser stub.
 *
 * Layout (stub, matches GHOSTNET_WIRE_SPEC_v1 draft):
 *
 *  byte 0: version (v1 = 1)
 *  byte 1: type (1 = AUDIO in this stub)
 *  byte 2..N: payload (for type=1: PCM bytes)
 */
data class ParsedFrame(
    val version: Int,
    val typeByte: Int,
    val payloadSize: Int
)

object FrameParserStub {

    private const val TAG = "FRAME_PARSER_STUB"
    private const val TYPE_AUDIO = 1  // stub mapping: 1 => AUDIO / PCM

    /**
     * Parse a raw inbound frame.
     *
     * For now we:
     *  - validate minimal header size,
     *  - extract version, type and payload,
     *  - if type == AUDIO, forward payload as PCM to MediaRouterInboundStub,
     *  - return a ParsedFrame summary for logging / debugging.
     */
    fun parse(raw: ByteArray): ParsedFrame? {
        if (raw.size < 2) {
            Log.w(TAG, "parse(): frame too short size=${raw.size}")
            return null
        }

        val version = raw[0].toInt() and 0xFF
        val typeByte = raw[1].toInt() and 0xFF
        val payload =
            if (raw.size > 2) raw.copyOfRange(2, raw.size) else ByteArray(0)

        Log.d(TAG, "parse(): v=$version typeByte=$typeByte payload=${payload.size}")

        if (typeByte == TYPE_AUDIO) {
            Log.d(
                TAG,
                "parse(): treating payload as PCM AUDIO, forwarding to MediaRouterInboundStub (size=${payload.size})"
            )
            MediaRouterInboundStub.handleDecodedPcm(payload)
        } else {
            Log.d(TAG, "parse(): non-AUDIO type, no media dispatch in stub")
        }

        return ParsedFrame(
            version = version,
            typeByte = typeByte,
            payloadSize = payload.size
        )
    }
}
KOT

echo "[OK] Updated FrameParserStub.kt to forward AUDIO payload to MediaRouterInboundStub"
echo "== patch_023 done =="

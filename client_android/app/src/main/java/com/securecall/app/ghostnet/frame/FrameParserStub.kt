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

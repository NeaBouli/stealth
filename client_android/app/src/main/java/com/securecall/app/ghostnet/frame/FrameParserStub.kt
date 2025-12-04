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

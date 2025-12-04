package com.securecall.app.ghostnet.frame.body

import android.util.Log
import com.securecall.app.ghostnet.frame.FrameType

/**
 * CRYPTO-38:
 * Zentraler Body-Parser für FrameHeaderV1 Frames.
 * Entscheidet anhand des FrameType, welche Body-Parser-Funktion genutzt wird.
 */
object FrameBodyParser {

    private const val TAG = "FRAME_BODY_PARSER"

    fun parse(type: FrameType, body: ByteArray): Any? {
        return when (type) {

            FrameType.AUDIO -> {
                Log.d(TAG, "parse(): AUDIO frame size=${body.size}")
                AudioBodyParser.parse(body)
            }

            FrameType.CONTROL -> {
                Log.d(TAG, "parse(): CONTROL frame size=${body.size}")
                ControlBodyParser.parse(body)
            }

            FrameType.KEEPALIVE -> {
                Log.d(TAG, "parse(): KEEPALIVE frame size=${body.size}")
                KeepAliveBodyParser.parse(body)
            }

            else -> {
                Log.w(TAG, "parse(): UNKNOWN frame type, size=${body.size}")
                null
            }
        }
    }
}

package com.securecall.app.ghostnet.frame

import android.util.Log
import org.json.JSONObject

/**
 * CRYPTO-21:
 * Minimaler FrameParser für die Payloads aus dem Ciphertext.
 */

object FrameParser {

    private const val TAG = "FRAME_PARSER"

    fun parse(payload: ByteArray): Any? {
        if (payload.size < 3) {
            Log.w(TAG, "Payload too small: ${payload.size}")
            return null
        }

        val typeId = payload[0]
        val t = FrameType.fromId(typeId) ?: return null

        val len = ((payload[1].toInt() and 0xFF) shl 8) or
                  (payload[2].toInt() and 0xFF)

        if (3 + len > payload.size) {
            Log.w(TAG, "Invalid length=$len for payloadSize=${payload.size}")
            return null
        }

        val data = payload.copyOfRange(3, 3 + len)

        return when (t) {
            FrameType.AUDIO -> AudioFrame(data)
            FrameType.CONTROL -> parseControl(data)
            FrameType.KEEPALIVE -> KeepAliveFrame()
        }
    }

    private fun parseControl(bytes: ByteArray): ControlFrame? {
        return try {
            val json = JSONObject(String(bytes))
            ControlFrame(
                code = json.getInt("code"),
                info = json.getString("info"),
                timestamp = json.getLong("ts")
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse ControlFrame", t)
            null
        }
    }
}

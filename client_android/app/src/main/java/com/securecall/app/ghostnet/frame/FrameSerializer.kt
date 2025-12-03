package com.securecall.app.ghostnet.frame

import org.json.JSONObject

/**
 * CRYPTO-21:
 * Serialisiert GhostNet-Frames in einfache Payloads.
 *
 * Format:
 *  [0]       = FrameType.id
 *  [1..2]    = length (2 bytes BigEndian)
 *  [3..end]  = payload
 */
object FrameSerializer {

    private fun encodeHeader(type: FrameType, length: Int): ByteArray {
        val header = ByteArray(3)
        header[0] = type.id
        header[1] = ((length shr 8) and 0xFF).toByte()
        header[2] = (length and 0xFF).toByte()
        return header
    }

    fun encodeAudio(frame: AudioFrame): ByteArray {
        val data = frame.data
        val header = encodeHeader(FrameType.AUDIO, data.size)
        return header + data
    }

    fun encodeControl(frame: ControlFrame): ByteArray {
        val json = JSONObject()
        json.put("code", frame.code)
        json.put("info", frame.info)
        json.put("ts", frame.timestamp)
        val payload = json.toString().encodeToByteArray()
        val header = encodeHeader(FrameType.CONTROL, payload.size)
        return header + payload
    }

    fun encodeKeepAlive(frame: KeepAliveFrame): ByteArray {
        val header = encodeHeader(FrameType.KEEPALIVE, 0)
        return header
    }
}

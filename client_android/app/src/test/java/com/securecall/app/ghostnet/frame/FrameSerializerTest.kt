package com.securecall.app.ghostnet.frame

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FrameSerializerTest {

    @Test
    fun encodeAudio_headerFormat() {
        val frame = AudioFrame(byteArrayOf(1, 2, 3))
        val encoded = FrameSerializer.encodeAudio(frame)
        assertEquals(FrameType.AUDIO.id, encoded[0])
        assertEquals(3 + 3, encoded.size) // 3 header + 3 data
    }

    @Test
    fun encodeAudio_lengthField() {
        val data = ByteArray(500)
        val encoded = FrameSerializer.encodeAudio(AudioFrame(data))
        val length = ((encoded[1].toInt() and 0xFF) shl 8) or (encoded[2].toInt() and 0xFF)
        assertEquals(500, length)
    }

    @Test
    fun encodeControl_containsJsonPayload() {
        val frame = ControlFrame(code = 100, info = "accept", timestamp = 123456L)
        val encoded = FrameSerializer.encodeControl(frame)
        assertEquals(FrameType.CONTROL.id, encoded[0])

        val json = JSONObject(String(encoded, 3, encoded.size - 3))
        assertEquals(100, json.getInt("code"))
        assertEquals("accept", json.getString("info"))
        assertEquals(123456L, json.getLong("ts"))
    }

    @Test
    fun encodeKeepAlive_headerOnly() {
        val encoded = FrameSerializer.encodeKeepAlive(KeepAliveFrame())
        assertEquals(3, encoded.size)
        assertEquals(FrameType.KEEPALIVE.id, encoded[0])
        assertEquals(0.toByte(), encoded[1])
        assertEquals(0.toByte(), encoded[2])
    }

    @Test
    fun encodeAudio_emptyData() {
        val encoded = FrameSerializer.encodeAudio(AudioFrame(ByteArray(0)))
        assertEquals(3, encoded.size)
    }
}

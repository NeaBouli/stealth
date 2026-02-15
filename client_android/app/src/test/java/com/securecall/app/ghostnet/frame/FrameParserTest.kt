package com.securecall.app.ghostnet.frame

import org.junit.Assert.*
import org.junit.Test

class FrameParserTest {

    @Test
    fun parse_audioFrame_roundtrip() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val encoded = FrameSerializer.encodeAudio(AudioFrame(data))
        val parsed = FrameParser.parse(encoded)
        assertTrue(parsed is AudioFrame)
        assertArrayEquals(data, (parsed as AudioFrame).data)
    }

    @Test
    fun parse_controlFrame_roundtrip() {
        val frame = ControlFrame(code = 42, info = "hello", timestamp = 999L)
        val encoded = FrameSerializer.encodeControl(frame)
        val parsed = FrameParser.parse(encoded)
        assertTrue(parsed is ControlFrame)
        val ctrl = parsed as ControlFrame
        assertEquals(42, ctrl.code)
        assertEquals("hello", ctrl.info)
        assertEquals(999L, ctrl.timestamp)
    }

    @Test
    fun parse_keepAliveFrame_roundtrip() {
        val encoded = FrameSerializer.encodeKeepAlive(KeepAliveFrame())
        val parsed = FrameParser.parse(encoded)
        assertTrue(parsed is KeepAliveFrame)
    }

    @Test
    fun parse_tooSmall_returnsNull() {
        assertNull(FrameParser.parse(ByteArray(2)))
        assertNull(FrameParser.parse(ByteArray(0)))
    }

    @Test
    fun parse_invalidTypeId_returnsNull() {
        val data = byteArrayOf(0x7F, 0x00, 0x00) // invalid type ID
        assertNull(FrameParser.parse(data))
    }

    @Test
    fun parse_lengthExceedsPayload_returnsNull() {
        // Header says length=100 but only 2 bytes of payload
        val data = byteArrayOf(FrameType.AUDIO.id, 0x00, 0x64, 0x01, 0x02)
        assertNull(FrameParser.parse(data))
    }
}

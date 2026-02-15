package com.securecall.app.ghostnet.frame.header

import org.junit.Assert.*
import org.junit.Test

class FrameHeaderV1Test {

    @Test
    fun parse_validHeader() {
        val bytes = byteArrayOf(1, 0x01, 0x00, 0x42)
        val h = FrameHeaderV1.parse(bytes)
        assertNotNull(h)
        assertEquals(1, h!!.version)
        assertEquals(1, h.flags)
        assertEquals(0, h.keyId)
        assertEquals(0x42, h.noncePrefix)
    }

    @Test
    fun parse_tooShort_returnsNull() {
        assertNull(FrameHeaderV1.parse(ByteArray(3)))
        assertNull(FrameHeaderV1.parse(ByteArray(0)))
    }

    @Test
    fun toBytes_roundtrip() {
        val original = FrameHeaderV1(1, 0x02, 3, 0xAB)
        val bytes = original.toBytes()
        val parsed = FrameHeaderV1.parse(bytes)
        assertNotNull(parsed)
        assertEquals(original.version, parsed!!.version)
        assertEquals(original.flags, parsed.flags)
        assertEquals(original.keyId, parsed.keyId)
        assertEquals(original.noncePrefix, parsed.noncePrefix)
    }

    @Test
    fun parse_extractsOnlyFirst4Bytes() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val h = FrameHeaderV1.parse(bytes)
        assertNotNull(h)
        assertEquals(1, h!!.version)
        assertEquals(2, h.flags)
        assertEquals(3, h.keyId)
        assertEquals(4, h.noncePrefix)
    }
}

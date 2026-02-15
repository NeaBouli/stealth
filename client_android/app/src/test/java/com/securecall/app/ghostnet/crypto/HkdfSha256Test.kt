package com.securecall.app.ghostnet.crypto

import org.junit.Assert.*
import org.junit.Test

class HkdfSha256Test {

    private val testSecret = ByteArray(32) { it.toByte() }

    @Test
    fun deriveKeys_returnsCorrectLength_32() {
        val result = HkdfSha256.deriveKeys(testSecret, "test-info", 32)
        assertEquals(32, result.size)
    }

    @Test
    fun deriveKeys_returnsCorrectLength_64() {
        val result = HkdfSha256.deriveKeys(testSecret, "test-info", 64)
        assertEquals(64, result.size)
    }

    @Test
    fun deriveKeys_returnsCorrectLength_16() {
        val result = HkdfSha256.deriveKeys(testSecret, "test-info", 16)
        assertEquals(16, result.size)
    }

    @Test
    fun deriveKeys_deterministicOutput() {
        val r1 = HkdfSha256.deriveKeys(testSecret, "info", 32)
        val r2 = HkdfSha256.deriveKeys(testSecret, "info", 32)
        assertArrayEquals(r1, r2)
    }

    @Test
    fun deriveKeys_differentInfo_differentOutput() {
        val r1 = HkdfSha256.deriveKeys(testSecret, "infoA", 32)
        val r2 = HkdfSha256.deriveKeys(testSecret, "infoB", 32)
        assertFalse(r1.contentEquals(r2))
    }

    @Test
    fun deriveKeys_differentSecret_differentOutput() {
        val secretA = ByteArray(32) { 0xAA.toByte() }
        val secretB = ByteArray(32) { 0xBB.toByte() }
        val r1 = HkdfSha256.deriveKeys(secretA, "info", 32)
        val r2 = HkdfSha256.deriveKeys(secretB, "info", 32)
        assertFalse(r1.contentEquals(r2))
    }

    @Test
    fun deriveKeys_emptyInfo() {
        val result = HkdfSha256.deriveKeys(testSecret, "", 32)
        assertEquals(32, result.size)
    }

    @Test
    fun deriveKeys_largeOutput() {
        val result = HkdfSha256.deriveKeys(testSecret, "info", 256)
        assertEquals(256, result.size)
    }
}

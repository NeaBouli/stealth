package com.securecall.app.ghostnet.crypto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReplayDetectorTest {

    @Before
    fun reset() {
        ReplayDetector.reset()
    }

    private fun getLastNonce(): Long {
        val field = ReplayDetector::class.java.getDeclaredField("lastNonce")
        field.isAccessible = true
        return field.getLong(ReplayDetector)
    }

    @Test
    fun check_initializesOnFirstNonce() {
        assertTrue(ReplayDetector.check(42))
        assertEquals(42L, getLastNonce())
    }

    @Test
    fun check_acceptsIncreasingNonces() {
        assertTrue(ReplayDetector.check(1))
        assertTrue(ReplayDetector.check(2))
        assertTrue(ReplayDetector.check(3))
        assertEquals(3L, getLastNonce())
    }

    @Test
    fun check_detectsReplay_returnsFalse() {
        assertTrue(ReplayDetector.check(5))
        assertFalse(ReplayDetector.check(5))
        assertEquals(5L, getLastNonce())
    }

    @Test
    fun check_detectsBackward_returnsFalse() {
        assertTrue(ReplayDetector.check(10))
        assertFalse(ReplayDetector.check(5))
        assertEquals(10L, getLastNonce())
    }

    @Test
    fun check_acceptsLargeGaps() {
        assertTrue(ReplayDetector.check(1))
        assertTrue(ReplayDetector.check(1_000_000))
        assertEquals(1_000_000L, getLastNonce())
    }

    @Test
    fun checkWithSecurity_initializesOnFirstNonce() {
        assertTrue(ReplayDetector.checkWithSecurity(99))
        assertEquals(99L, getLastNonce())
    }

    @Test
    fun checkWithSecurity_detectsReplay_returnsFalse() {
        assertTrue(ReplayDetector.checkWithSecurity(5))
        assertFalse(ReplayDetector.checkWithSecurity(5))
        assertEquals(5L, getLastNonce())
    }

    @Test
    fun isReplay_returnsTrueForDuplicate() {
        ReplayDetector.check(10)
        assertTrue(ReplayDetector.isReplay(10))
    }

    @Test
    fun isReplay_returnsFalseForNew() {
        ReplayDetector.check(10)
        assertFalse(ReplayDetector.isReplay(11))
    }

    @Test
    fun reset_clearsState() {
        ReplayDetector.check(100)
        ReplayDetector.reset()
        assertEquals(-1L, getLastNonce())
    }
}

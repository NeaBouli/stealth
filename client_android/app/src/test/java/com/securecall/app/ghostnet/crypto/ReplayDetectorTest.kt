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
        ReplayDetector.check(42)
        assertEquals(42L, getLastNonce())
    }

    @Test
    fun check_acceptsIncreasingNonces() {
        ReplayDetector.check(1)
        ReplayDetector.check(2)
        ReplayDetector.check(3)
        assertEquals(3L, getLastNonce())
    }

    @Test
    fun check_detectsReplay_doesNotUpdateLastNonce() {
        ReplayDetector.check(5)
        ReplayDetector.check(5)
        assertEquals(5L, getLastNonce())
    }

    @Test
    fun check_detectsBackward_doesNotUpdateLastNonce() {
        ReplayDetector.check(10)
        ReplayDetector.check(5)
        assertEquals(10L, getLastNonce())
    }

    @Test
    fun check_acceptsLargeGaps() {
        ReplayDetector.check(1)
        ReplayDetector.check(1_000_000)
        assertEquals(1_000_000L, getLastNonce())
    }

    @Test
    fun checkWithSecurity_initializesOnFirstNonce() {
        ReplayDetector.checkWithSecurity(99)
        assertEquals(99L, getLastNonce())
    }

    @Test
    fun checkWithSecurity_detectsReplay() {
        ReplayDetector.checkWithSecurity(5)
        ReplayDetector.checkWithSecurity(5)
        // Replay detected — lastNonce not advanced
        assertEquals(5L, getLastNonce())
    }

    @Test
    fun reset_clearsState() {
        ReplayDetector.check(100)
        ReplayDetector.reset()
        assertEquals(-1L, getLastNonce())
    }
}

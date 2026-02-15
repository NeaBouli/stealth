package com.securecall.app.ghostnet.crypto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class NonceManagerTest {

    @Before
    fun resetCounter() {
        val field = NonceManager::class.java.getDeclaredField("counter")
        field.isAccessible = true
        (field.get(NonceManager) as AtomicLong).set(1L)
    }

    @Test
    fun nextNonce_startsAt1() {
        assertEquals(1L, NonceManager.nextNonce())
    }

    @Test
    fun nextNonce_incrementsSequentially() {
        assertEquals(1L, NonceManager.nextNonce())
        assertEquals(2L, NonceManager.nextNonce())
        assertEquals(3L, NonceManager.nextNonce())
    }

    @Test
    fun nextNonce_neverReturnsZero() {
        repeat(100) {
            assertTrue(NonceManager.nextNonce() > 0)
        }
    }

    @Test
    fun nextNonce_overflowResets() {
        val field = NonceManager::class.java.getDeclaredField("counter")
        field.isAccessible = true
        (field.get(NonceManager) as AtomicLong).set(Long.MAX_VALUE)

        val value = NonceManager.nextNonce()
        // Overflow detected → reset to 1
        assertEquals(1L, value)
    }

    @Test
    fun nextNonce_threadSafety() {
        val seen = ConcurrentHashMap.newKeySet<Long>()
        val threads = (1..10).map {
            Thread {
                repeat(1000) {
                    seen.add(NonceManager.nextNonce())
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // All values should be unique (within the non-overflow range)
        assertEquals(10_000, seen.size)
    }
}

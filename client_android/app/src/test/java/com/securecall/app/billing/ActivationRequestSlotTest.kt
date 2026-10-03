package com.securecall.app.billing

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ActivationRequestSlotTest {
    private val callback: (Boolean, String, String) -> Unit = { _, _, _ -> }

    @Test
    fun overlappingAttemptCannotReplacePendingCallback() {
        val slot = ActivationRequestSlot()
        val first = requireNotNull(slot.begin(callback))
        assertNull(slot.begin(callback))
        assertSame(first, slot.take(first.id))
        assertNull(slot.take(first.id))
    }

    @Test
    fun staleTimeoutAndUncorrelatedReplyCannotConsumeNewRequest() {
        val slot = ActivationRequestSlot()
        val old = requireNotNull(slot.begin(callback))
        assertSame(old, slot.take(old.id))
        val next = requireNotNull(slot.begin(callback))
        assertNotEquals(old.id, next.id)
        assertNull(slot.take(old.id))
        assertNull(slot.take(""))
        assertSame(next, slot.take(next.id))
    }

    @Test
    fun shutdownClearsOnlyCurrentAttempt() {
        val slot = ActivationRequestSlot()
        val request = requireNotNull(slot.begin(callback))
        assertSame(request, slot.clear())
        assertNull(slot.clear())
        assertNull(slot.take(request.id))
        assertNotNull(slot.begin(callback))
    }

    @Test
    fun concurrentReplyAndTimeoutHaveExactlyOneWinner() {
        val slot = ActivationRequestSlot()
        val request = requireNotNull(slot.begin(callback))
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map {
                executor.submit<Boolean> {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    slot.take(request.id) != null
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }
    }
}

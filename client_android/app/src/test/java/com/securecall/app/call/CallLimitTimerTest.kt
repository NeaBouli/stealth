package com.securecall.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CallLimitTimer] with an injected fake scheduler —
 * covers the timer/call lifecycle without Android dependencies.
 */
class CallLimitTimerTest {

    /** Records the scheduled task instead of using a real Handler. */
    private class FakeScheduler {
        var scheduledDelayMs: Long? = null
            private set
        var task: Runnable? = null
            private set
        var cancelCount = 0
            private set

        val scheduler = CallLimitTimer.Scheduler { delayMs, runnable ->
            scheduledDelayMs = delayMs
            task = runnable
            CallLimitTimer.CancelHandle {
                cancelCount++
                task = null
            }
        }

        /** Simulate the Handler firing the scheduled task. */
        fun fire() {
            task?.run()
        }
    }

    @Test
    fun `start schedules the callback with the exact limit and firing triggers it`() {
        val fake = FakeScheduler()
        var fired = 0
        val timer = CallLimitTimer(900_000L, Runnable { fired++ }, fake.scheduler)

        timer.start()
        assertEquals(900_000L, fake.scheduledDelayMs)
        assertTrue(timer.isRunning)

        fake.fire()
        assertEquals(1, fired)
        assertFalse(timer.isRunning)
    }

    @Test
    fun `zero limit never schedules (unlimited paid tiers)`() {
        val fake = FakeScheduler()
        var fired = 0
        val timer = CallLimitTimer(0L, Runnable { fired++ }, fake.scheduler)

        timer.start()
        assertNull(fake.scheduledDelayMs)
        assertFalse(timer.isRunning)

        fake.fire()
        assertEquals(0, fired)
    }

    @Test
    fun `negative limit never schedules (invalid config treated as unlimited)`() {
        val fake = FakeScheduler()
        var fired = 0
        val timer = CallLimitTimer(-1L, Runnable { fired++ }, fake.scheduler)

        timer.start()
        assertNull(fake.scheduledDelayMs)
        assertFalse(timer.isRunning)
    }

    @Test
    fun `starting twice does not double-schedule`() {
        val fake = FakeScheduler()
        var fired = 0
        val timer = CallLimitTimer(60_000L, Runnable { fired++ }, fake.scheduler)

        timer.start()
        val firstTask = fake.task
        timer.start() // must be a no-op
        assertTrue(firstTask === fake.task)

        fake.fire()
        assertEquals(1, fired)
    }

    @Test
    fun `cancel before expiry prevents the callback (manual hangup path)`() {
        val fake = FakeScheduler()
        var fired = 0
        val timer = CallLimitTimer(60_000L, Runnable { fired++ }, fake.scheduler)

        timer.start()
        timer.cancel()
        assertFalse(timer.isRunning)
        assertEquals(1, fake.cancelCount)

        fake.fire() // task was unscheduled — nothing may fire
        assertEquals(0, fired)
    }

    @Test
    fun `cancel after firing is a safe no-op`() {
        val fake = FakeScheduler()
        var fired = 0
        val timer = CallLimitTimer(60_000L, Runnable { fired++ }, fake.scheduler)

        timer.start()
        fake.fire()
        timer.cancel()
        timer.cancel()
        assertEquals(1, fired)
        assertEquals(0, fake.cancelCount) // handle already consumed by firing
        assertFalse(timer.isRunning)
    }

    @Test
    fun `timer can be re-armed after cancel`() {
        val fake = FakeScheduler()
        var fired = 0
        val timer = CallLimitTimer(60_000L, Runnable { fired++ }, fake.scheduler)

        timer.start()
        timer.cancel()
        timer.start()
        assertTrue(timer.isRunning)

        fake.fire()
        assertEquals(1, fired)
    }
}

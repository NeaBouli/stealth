package com.securecall.app.call

/**
 * Countdown that ends a Free-tier call when its configured duration expires.
 *
 * Scheduling is injected so the timer is fully unit-testable on the JVM;
 * production code wires it to the main-thread Handler in CallActivity.
 *
 * A non-positive [limitMs] (unlimited or invalid configuration) never fires.
 */
class CallLimitTimer(
    private val limitMs: Long,
    private val onLimitReached: Runnable,
    private val scheduler: Scheduler
) {
    /** Schedules a task; returns a handle that unschedules it. */
    fun interface Scheduler {
        fun schedule(delayMs: Long, task: Runnable): CancelHandle
    }

    fun interface CancelHandle {
        fun cancel()
    }

    private var handle: CancelHandle? = null

    val isRunning: Boolean get() = handle != null

    /** Arm the timer. No-op when unlimited, invalid, or already running. */
    fun start() {
        if (limitMs <= 0L || handle != null) return
        handle = scheduler.schedule(limitMs, Runnable {
            handle = null
            onLimitReached.run()
        })
    }

    /** Disarm the timer; safe to call any number of times. */
    fun cancel() {
        handle?.cancel()
        handle = null
    }
}

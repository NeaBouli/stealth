package com.securecall.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Synthetic audio actions only: no codec, Android service, native loader or transport. */
class AudioPlaybackLifecycleTest {
    private fun await(latch: CountDownLatch) {
        check(latch.await(2, TimeUnit.SECONDS)) { "Audio lifecycle barrier timed out" }
    }

    @Test
    fun stopWaitsForDecodeAndEnqueueBeforeRelease() {
        val lifecycle = AudioPlaybackLifecycle()
        val generation = lifecycle.snapshot()
        val decoding = CountDownLatch(1)
        val finishDecode = CountDownLatch(1)
        val stopping = CountDownLatch(1)
        val released = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val pool = Executors.newFixedThreadPool(2)
        try {
            val consumer = pool.submit<Boolean> {
                lifecycle.consume(generation) {
                    events.add("decode")
                    decoding.countDown()
                    await(finishDecode)
                    events.add("enqueue/start")
                }
            }
            await(decoding)
            val stop = pool.submit {
                stopping.countDown()
                lifecycle.stop {
                    events.add("release")
                    released.countDown()
                }
            }
            await(stopping)
            assertFalse(released.await(100, TimeUnit.MILLISECONDS))
            finishDecode.countDown()
            assertTrue(consumer.get(2, TimeUnit.SECONDS))
            stop.get(2, TimeUnit.SECONDS)
            assertEquals(listOf("decode", "enqueue/start", "release"), events.toList())
        } finally {
            finishDecode.countDown()
            pool.shutdownNow()
            check(pool.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun callbackAdmittedBeforeStopCannotDecodeOrRestartAfterStop() {
        val lifecycle = AudioPlaybackLifecycle()
        val admitted = CountDownLatch(1)
        val authenticated = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val late = pool.submit<Boolean> {
                val generation = lifecycle.snapshot()
                admitted.countDown()
                await(authenticated)
                lifecycle.consume(generation) { error("Stopped callback restarted audio") }
            }
            await(admitted)
            lifecycle.stop {}
            authenticated.countDown()
            assertFalse(late.get(2, TimeUnit.SECONDS))
        } finally {
            authenticated.countDown()
            pool.shutdownNow()
            check(pool.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun freshCurrentCallAudioResumesButOldPlayoutCannotTouchReplacement() {
        val lifecycle = AudioPlaybackLifecycle()
        val oldPlayout = lifecycle.snapshot()
        lifecycle.stop {}
        var samples = 0
        assertTrue(lifecycle.consume(lifecycle.snapshot()) { samples += 960 })
        assertFalse(lifecycle.consume(oldPlayout) { error("Old playout touched replacement") })
        lifecycle.stop {}
        assertFalse(lifecycle.consume(oldPlayout) { error("Old generation became valid again") })
        assertTrue(lifecycle.consume(lifecycle.snapshot()) { samples += 960 })
        assertEquals(1920, samples)
    }

    @Test
    fun stopNeverNeedsTheCallOwnedMonitor() {
        val lifecycle = AudioPlaybackLifecycle()
        val callMonitor = Any()
        val callHeld = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val call = pool.submit<Boolean> {
                synchronized(callMonitor) {
                    val generation = lifecycle.snapshot()
                    callHeld.countDown()
                    await(stopped)
                    lifecycle.consume(generation) { error("Old consumer survived stop") }
                }
            }
            await(callHeld)
            val stop = pool.submit { lifecycle.stop {}; stopped.countDown() }
            stop.get(2, TimeUnit.SECONDS)
            assertFalse(call.get(2, TimeUnit.SECONDS))
        } finally {
            stopped.countDown()
            pool.shutdownNow()
            check(pool.awaitTermination(2, TimeUnit.SECONDS))
        }
    }
}

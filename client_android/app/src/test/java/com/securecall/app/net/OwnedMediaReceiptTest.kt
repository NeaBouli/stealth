package com.securecall.app.net

import android.util.Log
import com.securecall.app.audio.jitter.JitterBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * SC-OWNED-MEDIA-RECEIPT-20261119: only an authenticated decoded PCM array marked by its owner,
 * dequeued and written with a positive AudioTrack result may count. Pure JVM, synthetic arrays.
 */
class OwnedMediaReceiptTest {
    private val ownerA = Any()
    private val ownerB = Any()

    private fun receipt(): OwnedMediaReceipt = requireNotNull(OwnedMediaReceipt.createIfActive(true, "10.0.2.2"))
    private fun frame(): ShortArray = ShortArray(960)

    @Test
    fun activeOnlyForDebugWithOwnedHost() {
        assertNull(OwnedMediaReceipt.createIfActive(false, "10.0.2.2"))
        assertNull(OwnedMediaReceipt.createIfActive(true, ""))
        assertNull(OwnedMediaReceipt.createIfActive(false, ""))
        assertNotNull(OwnedMediaReceipt.createIfActive(true, "127.0.0.1"))
    }

    @Test
    fun authenticatedSilentDecodedFrameWrittenInFullCountsOnce() {
        val r = receipt()
        val pcm = frame() // all-zero authenticated audio is valid data-path proof
        r.decoded(ownerA, pcm)
        r.dequeued(pcm, playable = true)
        r.written(pcm, pcm.size)
        assertEquals(OwnedMediaReceipt.Snapshot(1, 0, 0), r.snapshot())
        r.written(pcm, pcm.size) // re-write of the same array is not a new frame
        assertEquals(1, r.snapshot().consumed)
    }

    @Test
    fun underflowOrReplacementSilenceNeverCountsEvenWithEqualContent() {
        val r = receipt()
        val decoded = frame()
        r.decoded(ownerA, decoded)
        val silence = ShortArray(960) // equal content, different array
        r.dequeued(silence, playable = true)
        r.written(silence, silence.size)
        assertEquals(OwnedMediaReceipt.Snapshot(0, 0, 1), r.snapshot())
    }

    @Test
    fun unmarkedFrameFromFailedAuthOrDecodeNeverCounts() {
        val r = receipt()
        val pcm = frame() // never marked: consumer not reached or decode returned empty
        r.written(pcm, pcm.size)
        assertEquals(OwnedMediaReceipt.Snapshot(0, 0, 0), r.snapshot())
    }

    @Test
    fun partialWriteCountsAsConsumedAndPartial() {
        val r = receipt()
        val pcm = frame()
        r.decoded(ownerA, pcm)
        r.dequeued(pcm, playable = true)
        r.written(pcm, 480)
        assertEquals(OwnedMediaReceipt.Snapshot(1, 1, 0), r.snapshot())
    }

    @Test
    fun zeroAndNegativeWritesNeverCountAndConsumeTheMark() {
        val r = receipt()
        val zero = frame()
        val negative = frame()
        r.decoded(ownerA, zero)
        r.decoded(ownerA, negative)
        r.dequeued(zero, playable = true)
        r.written(zero, 0)
        r.dequeued(negative, playable = true)
        r.written(negative, -3)
        assertEquals(OwnedMediaReceipt.Snapshot(0, 0, 0), r.snapshot())
        r.written(zero, zero.size) // a later write cannot resurrect a failed attempt
        assertEquals(0, r.snapshot().consumed)
    }

    @Test
    fun replacedOwnerDropsOldMarksAndCounts() {
        val r = receipt()
        val old = frame()
        r.decoded(ownerA, old)
        val counted = frame()
        r.decoded(ownerA, counted)
        r.dequeued(old, playable = true)
        r.written(old, old.size)
        assertEquals(1, r.snapshot().consumed)
        r.dequeued(counted, playable = true)
        val fresh = frame()
        r.decoded(ownerB, fresh)
        assertEquals(OwnedMediaReceipt.Snapshot(0, 0, 1), r.snapshot())
        r.written(counted, counted.size) // owner A frame after replacement
        assertEquals(0, r.snapshot().consumed)
        r.dequeued(fresh, playable = true)
        r.written(fresh, fresh.size)
        assertEquals(OwnedMediaReceipt.Snapshot(1, 0, 0), r.snapshot())
    }

    @Test
    fun stopResetDropsOwnerMarksAndCounts() {
        val r = receipt()
        val first = frame()
        val inFlight = frame()
        r.decoded(ownerA, first)
        r.decoded(ownerA, inFlight)
        r.dequeued(first, playable = true)
        r.written(first, first.size)
        r.dequeued(inFlight, playable = true)
        r.reset()
        assertEquals(OwnedMediaReceipt.Snapshot(0, 0, 0), r.snapshot())
        r.written(inFlight, inFlight.size) // popped before stop, written after it
        assertEquals(0, r.snapshot().consumed)
    }

    @Test
    fun marksStayBoundedByJitterCapacityAndFollowFifoOrder() {
        val r = receipt()
        val frames = List(JitterBuffer.MAX_SIZE + 1) { frame() }
        frames.forEach { r.decoded(ownerA, it) }
        assertEquals(JitterBuffer.MAX_SIZE, r.snapshot().pending)
        r.dequeued(frames[0], playable = true)
        r.written(frames[0], 960) // overflow-dropped by the queue: never dequeued
        assertEquals(0, r.snapshot().consumed)
        r.dequeued(frames[2], playable = true)
        r.written(frames[2], 960) // dequeuing frame 2 retires the older frame 1
        r.dequeued(frames[1], playable = true)
        r.written(frames[1], 960)
        assertEquals(OwnedMediaReceipt.Snapshot(1, 0, JitterBuffer.MAX_SIZE - 2), r.snapshot())
    }

    @Test
    fun markedButNotDequeuedAndPausedDiscardCannotCount() {
        val r = receipt()
        val pcm = frame()
        r.decoded(ownerA, pcm)
        r.written(pcm, pcm.size)
        assertEquals(OwnedMediaReceipt.Snapshot(0, 0, 1), r.snapshot())
        r.dequeued(pcm, playable = false)
        r.written(pcm, pcm.size)
        assertEquals(OwnedMediaReceipt.Snapshot(0, 0, 0), r.snapshot())
    }

    @Test
    fun poppedFrameSurvivesFullQueueAndActualQueuedOverflow() {
        val r = receipt()
        val popped = frame()
        JitterBuffer.clear()
        try {
            r.decoded(ownerA, popped)
            JitterBuffer.push(popped)
            r.dequeued(requireNotNull(JitterBuffer.pop()), playable = true)
            val queued = List(JitterBuffer.MAX_SIZE + 1) { frame() }
            queued.forEach { r.decoded(ownerA, it); JitterBuffer.push(it) }
            assertEquals(JitterBuffer.MAX_SIZE, JitterBuffer.size())
            assertEquals(OwnedMediaReceipt.Snapshot(0, 0, JitterBuffer.MAX_SIZE, 1), r.snapshot())
            r.written(popped, popped.size)
            assertEquals(OwnedMediaReceipt.Snapshot(1, 0, JitterBuffer.MAX_SIZE), r.snapshot())
            r.dequeued(queued[0], playable = true) // actual queue overflow discarded this one
            r.written(queued[0], queued[0].size)
            assertEquals(1, r.snapshot().consumed)
        } finally { JitterBuffer.clear() }
    }

    @Test
    fun emissionFinishesBeforeResetOrReplacementCanComplete() {
        for (phase in listOf("progress", "final")) {
            val r = receipt()
            val pcm = frame()
            val entered = CountDownLatch(1)
            val replaced = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val replacement = Thread {
                entered.countDown()
                try {
                    if (phase == "progress") r.reset()
                    r.decoded(ownerB, frame())
                } catch (error: Throwable) { failure.set(error) }
                finally { replaced.countDown() }
            }
            r.decoded(ownerA, pcm)
            r.dequeued(pcm, playable = true)
            if (phase == "final") r.written(pcm, pcm.size)
            Mockito.mockStatic(Log::class.java).use { logger ->
                logger.`when`<Int> {
                    Log.i("OWNED_MEDIA_RECEIPT", "owned media receipt $phase: consumed=1 partial=0")
                }.thenAnswer {
                    replacement.start()
                    check(entered.await(2, TimeUnit.SECONDS))
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                    while (replacement.state != Thread.State.BLOCKED && replaced.count > 0
                        && System.nanoTime() < deadline) Thread.yield()
                    assertEquals(Thread.State.BLOCKED, replacement.state)
                    assertEquals(1L, replaced.count)
                    assertEquals(OwnedMediaReceipt.Snapshot(if (phase == "progress") 1 else 0, 0, 0), r.snapshot())
                    0
                }
                try { if (phase == "progress") r.written(pcm, pcm.size) else r.reset() }
                finally { replacement.join(3_000) }
            }
            assertEquals(0L, replaced.count)
            assertEquals(false, replacement.isAlive)
            assertNull(failure.get())
            assertEquals(OwnedMediaReceipt.Snapshot(0, 0, 1), r.snapshot())
        }
    }
}

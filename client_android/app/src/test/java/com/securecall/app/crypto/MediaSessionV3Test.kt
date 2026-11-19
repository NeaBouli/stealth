package com.securecall.app.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * State-machine tests for the per-call media session. The AEAD itself is covered by
 * core_crypto `aead::tests::media_v3_*`; here a synthetic keyed-MAC cipher with the same
 * contract (role/context/sequence checks, replay state in/out) isolates sequencing,
 * atomic commit, concurrency and teardown. Synthetic keys only.
 */
class MediaSessionV3Test {
    private val sessionId = "00000000-0000-4000-8000-000000000001"
    private val inviteDigest = "A".repeat(43)
    private val acceptDigest = "B".repeat(43)

    private class FakeCipher : MediaFrameCipher {
        val opens = AtomicInteger()

        private fun tag(key: ByteArray, body: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(body).copyOf(16)
        }

        override fun seal(key: ByteArray, senderRole: Int, context: ByteArray, sequence: Long, plaintext: ByteArray): ByteArray {
            val header = ByteBuffer.allocate(MediaSessionV3.HEADER_SIZE)
                .put(senderRole.toByte()).putLong(sequence).put(context).array()
            val body = header + plaintext
            return body + tag(key, body)
        }

        override fun open(
            key: ByteArray, expectedPeerRole: Int, context: ByteArray,
            highest: Long, bitmap: Long, frame: ByteArray,
        ): ByteArray? {
            opens.incrementAndGet()
            val body = frame.copyOfRange(0, frame.size - 16)
            if (!tag(key, body).contentEquals(frame.copyOfRange(frame.size - 16, frame.size))) return null
            val buffer = ByteBuffer.wrap(frame)
            if (buffer.get().toInt() != expectedPeerRole) return null
            val seq = buffer.long
            if (!frame.copyOfRange(9, 41).contentEquals(context) || seq < 1) return null
            // RFC 6479 window, as core_crypto ReplayDetector.
            var h = highest
            var b = bitmap
            if (seq > h) {
                val diff = seq - h
                b = if (diff >= 64) 0 else b shl diff.toInt()
                b = b or 1
                h = seq
            } else {
                val diff = h - seq
                if (diff >= 64 || (b and (1L shl diff.toInt())) != 0L) return null
                b = b or (1L shl diff.toInt())
            }
            return ByteBuffer.allocate(16).putLong(h).putLong(b).array() +
                body.copyOfRange(MediaSessionV3.HEADER_SIZE, body.size)
        }
    }

    private fun keys(seed: Int = 7) =
        requireNotNull(SessionKeyBinding.deriveV3(ByteArray(32) { (it + seed).toByte() }, inviteDigest, acceptDigest))

    private fun pair(cipher: MediaFrameCipher = FakeCipher(), seed: Int = 7): Pair<MediaSessionV3, MediaSessionV3> {
        val k = keys(seed)
        return MediaSessionV3(sessionId, true, k, cipher) to MediaSessionV3(sessionId, false, k, cipher)
    }

    private fun sequenceOf(frame: ByteArray) = ByteBuffer.wrap(frame, 1, 8).long

    /** Test-only copy through the production consume boundary. */
    private fun MediaSessionV3.openCopy(frame: ByteArray): ByteArray? {
        var copy: ByteArray? = null
        consume(frame) { copy = it.copyOf() }
        return copy
    }

    @Test
    fun legitimateTwoDirectionCallUsesSeparateKeysAndIncreasingSequences() {
        val (caller, callee) = pair()
        val up = (1..3).map { requireNotNull(caller.seal(byteArrayOf(it.toByte()))) }
        assertEquals(listOf(1L, 2L, 3L), up.map(::sequenceOf))
        up.forEachIndexed { i, f -> assertArrayEquals(byteArrayOf((i + 1).toByte()), callee.openCopy(f)) }
        val down = requireNotNull(callee.seal(byteArrayOf(9)))
        assertEquals(1L, sequenceOf(down))
        assertArrayEquals(byteArrayOf(9), caller.openCopy(down))
        // Same sequence, different direction: different key, so the tag differs.
        assertTrue(!up[0].copyOfRange(up[0].size - 16, up[0].size)
            .contentEquals(down.copyOfRange(down.size - 16, down.size)))
    }

    @Test
    fun reflectionDuplicateAndOldFramesAreRejected() {
        val (caller, callee) = pair()
        val own = requireNotNull(caller.seal(byteArrayOf(1)))
        assertNull(caller.openCopy(own))
        assertNotNull(callee.openCopy(own))
        assertNull(callee.openCopy(own))
        repeat(70) { caller.seal(byteArrayOf(2)) }
        val newest = requireNotNull(caller.seal(byteArrayOf(3)))
        assertNotNull(callee.openCopy(newest))
        assertNull(callee.openCopy(own))
    }

    @Test
    fun forgedHighSequenceCannotPoisonCommittedWindow() {
        val (caller, callee) = pair()
        assertNotNull(callee.openCopy(requireNotNull(caller.seal(byteArrayOf(1)))))
        val forged = requireNotNull(caller.seal(byteArrayOf(5))).also {
            ByteBuffer.wrap(it, 1, 8).putLong(Long.MAX_VALUE - 1)
        }
        assertNull(callee.openCopy(forged))
        val attacker = MediaSessionV3(sessionId, true, keys(seed = 99), FakeCipher())
        repeat(3) { attacker.seal(byteArrayOf(0)) }
        assertNull(callee.openCopy(requireNotNull(attacker.seal(byteArrayOf(6)))))
        // Committed state unchanged: the next legitimate frames still open.
        assertNotNull(callee.openCopy(requireNotNull(caller.seal(byteArrayOf(7)))))
    }

    @Test
    fun malformedSizesAreRejectedBeforeTheCipher() {
        val cipher = FakeCipher()
        val (caller, callee) = pair(cipher)
        assertNull(callee.openCopy(ByteArray(MediaSessionV3.MIN_FRAME - 1)))
        assertNull(callee.openCopy(ByteArray(MediaSessionV3.MAX_FRAME + 1)))
        assertNull(callee.openCopy(ByteArray(0)))
        assertEquals(0, cipher.opens.get())
        assertNull(caller.seal(ByteArray(0)))
        assertNull(caller.seal(ByteArray(MediaSessionV3.MAX_PLAINTEXT + 1)))
        assertNotNull(caller.seal(ByteArray(MediaSessionV3.MAX_PLAINTEXT)))
    }

    @Test
    fun sequenceExhaustionFailsClosed() {
        val k = keys()
        val caller = MediaSessionV3(sessionId, true, k, FakeCipher(), firstSequence = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, sequenceOf(requireNotNull(caller.seal(byteArrayOf(1)))))
        assertNull(caller.seal(byteArrayOf(1)))
        assertNull(caller.seal(byteArrayOf(1)))
    }

    @Test
    fun concurrentReceivesAcceptEachSequenceExactlyOnce() {
        val (caller, callee) = pair()
        val frames = (1..200).map { requireNotNull(caller.seal(byteArrayOf(it.toByte()))) }
        val accepted = ConcurrentLinkedQueue<Int>()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(4) {
            pool.execute {
                start.await()
                frames.forEach { f -> callee.openCopy(f)?.let { accepted.add(it[0].toInt() and 0xff) } }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        // Every frame is accepted by exactly one receiver thread; duplicates never commit twice.
        assertEquals(200, accepted.size)
        assertEquals((1..200).toSet(), accepted.toSet())
    }

    @Test
    fun teardownClearsKeysAndStateAndNewCallRejectsStaleFrames() {
        val (caller, callee) = pair()
        val stale = requireNotNull(caller.seal(byteArrayOf(1)))
        callee.close()
        caller.close()
        assertTrue(callee.isClosed)
        assertNull(callee.openCopy(stale))
        assertNull(caller.seal(byteArrayOf(1)))

        val (newCaller, newCallee) = pair(seed = 11)
        assertNull(newCallee.openCopy(stale))
        assertNotNull(newCallee.openCopy(requireNotNull(newCaller.seal(byteArrayOf(2)))))
    }

    @Test
    fun transportRestartKeepsTheSenderSequence() {
        val (caller, callee) = pair()
        repeat(3) { assertNotNull(callee.openCopy(requireNotNull(caller.seal(byteArrayOf(1))))) }
        // ICE restart / relay retry replaces only the WebRTC transport; the session survives.
        val afterRestart = requireNotNull(caller.seal(byteArrayOf(2)))
        assertEquals(4L, sequenceOf(afterRestart))
        assertNotNull(callee.openCopy(afterRestart))
    }

    @Test
    fun closeWaitsForInFlightConsumerAndNoConsumerRunsAfterCloseReturns() {
        val (caller, callee) = pair()
        val first = requireNotNull(caller.seal(byteArrayOf(1)))
        val second = requireNotNull(caller.seal(byteArrayOf(2)))
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val consumerEntered = CountDownLatch(1)
        val releaseConsumer = CountDownLatch(1)
        var seen: ByteArray? = null
        val receiver = Thread {
            callee.consume(first) { plain ->
                seen = plain
                events += "consumer-start"
                consumerEntered.countDown()
                releaseConsumer.await()
                events += "consumer-end"
            }
        }
        receiver.start()
        assertTrue(consumerEntered.await(5, TimeUnit.SECONDS))
        val closer = Thread { callee.close(); events += "close-returned" }
        closer.start()
        // Deterministic overlap: the closer is parked on the session lock while the consumer runs.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (closer.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield()
        assertEquals(Thread.State.BLOCKED, closer.state)
        assertEquals(listOf("consumer-start"), events.toList())
        releaseConsumer.countDown()
        receiver.join(5_000)
        closer.join(5_000)
        assertEquals(listOf("consumer-start", "consumer-end", "close-returned"), events.toList())
        // Temporary plaintext is wiped once the consumer returns.
        assertTrue(requireNotNull(seen).all { it == 0.toByte() })
        var lateConsumer = false
        assertEquals(false, callee.consume(second) { lateConsumer = true })
        assertEquals(false, lateConsumer)
    }

    @Test
    fun replayBitmapSignBitSurvivesTheLongStateRoundTrip() {
        // Kotlin carries the native u64 bitmap as a signed Long; bit 63 is the sign bit.
        // The fake mirrors core_crypto ReplayDetector; the real detector is covered by
        // aead::tests::media_v3_replay_bitmap_bit63_roundtrip. Not JNI proof.
        val (caller, callee) = pair()
        val frames = (1..65).map { requireNotNull(caller.seal(byteArrayOf(it.toByte()))) }
        assertNotNull(callee.openCopy(frames[0]))
        assertNotNull(callee.openCopy(frames[63]))
        assertNull(callee.openCopy(frames[0]))
        assertNotNull(callee.openCopy(frames[1]))
        assertNotNull(callee.openCopy(frames[64]))
        assertNull(callee.openCopy(frames[0]))
        assertNull(callee.openCopy(frames[1]))
        assertNotNull(callee.openCopy(frames[2]))
    }

    @Test
    fun concurrentSealOpenAndCloseNeverConsumeAfterClose() {
        val (caller, callee) = pair()
        val frames = (1..300).map { requireNotNull(caller.seal(byteArrayOf((it % 251).toByte()))) }
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        val lateConsumers = AtomicInteger()
        val sealed = ConcurrentLinkedQueue<Long>()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(6)
        repeat(3) {
            pool.execute {
                try {
                    start.await()
                    frames.forEach { f ->
                        callee.consume(f) { if (closed.get()) lateConsumers.incrementAndGet() }
                    }
                } catch (t: Throwable) { failures += t }
            }
        }
        repeat(2) {
            pool.execute {
                try {
                    start.await()
                    repeat(200) { callee.seal(byteArrayOf(1))?.let { sealed += sequenceOf(it) } }
                } catch (t: Throwable) { failures += t }
            }
        }
        pool.execute {
            try {
                start.await()
                Thread.yield()
                callee.close()
                closed.set(true)
            } catch (t: Throwable) { failures += t }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertTrue(failures.isEmpty())
        assertEquals(0, lateConsumers.get())
        assertEquals(sealed.size, sealed.toSet().size)
        assertNull(callee.seal(byteArrayOf(1)))
        assertNull(callee.openCopy(frames.last()))
    }
}

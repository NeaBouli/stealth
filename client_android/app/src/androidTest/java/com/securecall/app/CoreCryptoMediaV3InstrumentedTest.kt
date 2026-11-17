package com.securecall.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.securecall.crypto.CoreCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/**
 * STX-24 media v3 through the packaged Rust library and real Android JNI.
 *
 * No fake cipher, reflection or skip: a missing or stale libsecurecall_core_crypto.so
 * fails in [requireNativeLibrary] or with UnsatisfiedLinkError on the first media call.
 * Keys, context and payloads are synthetic constants.
 */
@RunWith(AndroidJUnit4::class)
class CoreCryptoMediaV3InstrumentedTest {

    private data class Opened(val highest: Long, val bitmap: Long, val plaintext: ByteArray)

    @Before
    fun requireNativeLibrary() {
        assertTrue("libsecurecall_core_crypto must load", CoreCrypto.isNativeAvailable())
        assertTrue("Rust self-test must pass", CoreCrypto.selfTest())
    }

    @Test
    fun bidirectionalRoundtripUsesDirectionKeysAndCommitsReplayState() {
        val first = seal(CALLER_KEY, CALLER, 1, PAYLOAD)
        assertEquals(3, first[0].toInt())
        assertEquals(1, first[1].toInt())
        assertEquals(CALLER, first[2].toInt())
        assertEquals(0, first[3].toInt())
        assertEquals(1L, ByteBuffer.wrap(first, 4, 8).long)
        assertArrayEquals(CONTEXT, first.copyOfRange(12, HEADER))
        assertFalse(first.copyOfRange(HEADER, first.size - TAG).contentEquals(PAYLOAD))

        val calleeGot = open(CALLER_KEY, CALLER, 0, 0, first)!!
        assertArrayEquals(PAYLOAD, calleeGot.plaintext)
        assertEquals(1L, calleeGot.highest)
        assertEquals(1L, calleeGot.bitmap)
        val second = open(CALLER_KEY, CALLER, calleeGot.highest, calleeGot.bitmap,
            seal(CALLER_KEY, CALLER, 2, PAYLOAD))!!
        assertEquals(2L, second.highest)
        assertEquals(3L, second.bitmap)

        val reply = byteArrayOf(9, 8, 7, 6, 5)
        val callerGot = open(CALLEE_KEY, CALLEE, 0, 0, seal(CALLEE_KEY, CALLEE, 1, reply))!!
        assertArrayEquals(reply, callerGot.plaintext)
        assertEquals(1L, callerGot.highest)
    }

    @Test
    fun reflectedOrWrongRoleFramesAreRejected() {
        val frame = seal(CALLER_KEY, CALLER, 1, PAYLOAD)
        // Caller receives its own frame back: expects the callee role and key.
        assertNull(open(CALLEE_KEY, CALLEE, 0, 0, frame))
        assertNull(open(CALLER_KEY, CALLEE, 0, 0, frame))
        // Rewriting the role byte breaks AEAD (role is in header AAD and nonce).
        assertNull(open(CALLER_KEY, CALLEE, 0, 0, frame.with(2, CALLEE)))
        for (role in intArrayOf(0, 3, -1)) {
            assertNull(CoreCrypto.mediaSeal(CALLER_KEY, role, CONTEXT, 1, PAYLOAD))
            assertNull(open(CALLER_KEY, role, 0, 0, frame))
        }
        assertNotNull(open(CALLER_KEY, CALLER, 0, 0, frame))
    }

    @Test
    fun wrongContextKeyOrTagIsRejected() {
        val frame = seal(CALLER_KEY, CALLER, 1, PAYLOAD)
        val otherContext = ByteArray(32) { 0x5a }
        assertNull(open(CALLER_KEY, CALLER, 0, 0, frame, otherContext))
        val relabelled = frame.copyOf().also { otherContext.copyInto(it, 12) }
        assertNull(open(CALLER_KEY, CALLER, 0, 0, relabelled, otherContext))
        assertNull(open(CALLEE_KEY, CALLER, 0, 0, frame))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, frame.with(frame.size - 1, frame.last() + 1)))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, frame.with(HEADER, frame[HEADER] + 1)))
        assertNull(CoreCrypto.mediaOpen(ByteArray(31), CALLER, CONTEXT, 0, 0, frame))
        assertNull(CoreCrypto.mediaOpen(CALLER_KEY, CALLER, ByteArray(31), 0, 0, frame))
        assertNull(CoreCrypto.mediaSeal(ByteArray(31), CALLER, CONTEXT, 1, PAYLOAD))
        assertNull(CoreCrypto.mediaSeal(CALLER_KEY, CALLER, ByteArray(33), 1, PAYLOAD))
        assertNotNull(open(CALLER_KEY, CALLER, 0, 0, frame))
    }

    @Test
    fun replayWindowAndForgedHighSequenceDoNotPoisonState() {
        var state = Opened(0, 0, ByteArray(0))
        for (seq in 1L..3L) state = open(CALLER_KEY, CALLER, state.highest, state.bitmap, frame(seq))!!
        assertNull(open(CALLER_KEY, CALLER, state.highest, state.bitmap, frame(2)))

        state = open(CALLER_KEY, CALLER, state.highest, state.bitmap, frame(70))!!
        assertEquals(70L, state.highest)
        assertEquals(1L, state.bitmap)
        assertNull("67 behind highest is outside the 64 window",
            open(CALLER_KEY, CALLER, state.highest, state.bitmap, frame(3)))

        // Forged maximum sequence with an untouched tag fails authentication; the caller
        // keeps its committed state and later genuine frames still open.
        val forged = frame(71).also { ByteBuffer.wrap(it).putLong(4, Long.MAX_VALUE) }
        assertNull(open(CALLER_KEY, CALLER, state.highest, state.bitmap, forged))
        state = open(CALLER_KEY, CALLER, state.highest, state.bitmap, frame(71))!!
        assertEquals(71L, state.highest)
        assertEquals(3L, state.bitmap)
        val late = open(CALLER_KEY, CALLER, state.highest, state.bitmap, frame(69))!!
        assertEquals(7L, late.bitmap)
        assertNull(open(CALLER_KEY, CALLER, late.highest, late.bitmap, frame(69)))
    }

    @Test
    fun negativeJvmBitmapPreservesBit63() {
        val highest = 100L
        val bitmap = Long.MIN_VALUE or 1L // sequences 37 and 100 already seen
        val opened = open(CALLER_KEY, CALLER, highest, bitmap, frame(99))!!
        assertEquals(100L, opened.highest)
        assertEquals(Long.MIN_VALUE or 3L, opened.bitmap)
        assertTrue(opened.bitmap < 0)
        assertNull(open(CALLER_KEY, CALLER, opened.highest, opened.bitmap, frame(37)))
        assertNull(open(CALLER_KEY, CALLER, opened.highest, opened.bitmap, frame(36)))
        val shifted = open(CALLER_KEY, CALLER, opened.highest, opened.bitmap, frame(101))!!
        assertEquals(101L, shifted.highest)
        assertEquals(7L, shifted.bitmap)
    }

    @Test
    fun plaintextSizeBoundaries4000And4001() {
        val max = ByteArray(MAX_PLAINTEXT) { (it * 31).toByte() }
        val maxFrame = seal(CALLER_KEY, CALLER, 1, max)
        assertEquals(HEADER + MAX_PLAINTEXT + TAG, maxFrame.size)
        assertArrayEquals(max, open(CALLER_KEY, CALLER, 0, 0, maxFrame)!!.plaintext)
        assertNull(CoreCrypto.mediaSeal(CALLER_KEY, CALLER, CONTEXT, 1, ByteArray(MAX_PLAINTEXT + 1)))
        assertNull(CoreCrypto.mediaSeal(CALLER_KEY, CALLER, CONTEXT, 1, ByteArray(0)))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, maxFrame + 0.toByte()))

        val minFrame = seal(CALLER_KEY, CALLER, 2, byteArrayOf(42))
        assertEquals(HEADER + 1 + TAG, minFrame.size)
        assertArrayEquals(byteArrayOf(42), open(CALLER_KEY, CALLER, 0, 0, minFrame)!!.plaintext)
        assertNull(open(CALLER_KEY, CALLER, 0, 0, minFrame.copyOf(minFrame.size - 1)))
    }

    @Test
    fun sequenceZeroNegativeAndMaxBoundaries() {
        for (seq in longArrayOf(0, -1, Long.MIN_VALUE)) {
            assertNull(CoreCrypto.mediaSeal(CALLER_KEY, CALLER, CONTEXT, seq, PAYLOAD))
        }
        val top = open(CALLER_KEY, CALLER, 0, 0, frame(Long.MAX_VALUE))!!
        assertEquals(Long.MAX_VALUE, top.highest)
        assertEquals(1L, top.bitmap)
        val base = frame(1)
        assertNull(open(CALLER_KEY, CALLER, 0, 0, base.copyOf().also { ByteBuffer.wrap(it).putLong(4, 0) }))
        assertNull(open(CALLER_KEY, CALLER, 0, 0,
            base.copyOf().also { ByteBuffer.wrap(it).putLong(4, Long.MIN_VALUE) }))
        assertNull("negative committed highest is rejected", open(CALLER_KEY, CALLER, -1, 0, base))
        assertNotNull(open(CALLER_KEY, CALLER, 0, 0, base))
    }

    @Test
    fun legacyAndMalformedEnvelopesAreRejected() {
        val legacy = CoreCrypto.encrypt(CALLER_KEY, ByteArray(64) { it.toByte() })
        assertNotNull("legacy encrypt must still be exported", legacy)
        assertNull(open(CALLER_KEY, CALLER, 0, 0, legacy))

        val frame = seal(CALLER_KEY, CALLER, 1, PAYLOAD)
        assertNull(open(CALLER_KEY, CALLER, 0, 0, frame.with(0, 2)))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, frame.with(0, 4)))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, frame.with(1, 2)))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, frame.with(3, 1)))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, frame.copyOf(frame.size - 1)))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, ByteArray(100)))
        assertNull(open(CALLER_KEY, CALLER, 0, 0, ByteArray(0)))
        assertNotNull(open(CALLER_KEY, CALLER, 0, 0, frame))
    }

    private fun frame(seq: Long) = seal(CALLER_KEY, CALLER, seq, PAYLOAD)

    private fun seal(key: ByteArray, role: Int, seq: Long, plain: ByteArray): ByteArray {
        val frame = CoreCrypto.mediaSeal(key, role, CONTEXT, seq, plain)
        assertNotNull("mediaSeal must succeed for seq=$seq", frame)
        assertEquals(HEADER + plain.size + TAG, frame.size)
        return frame
    }

    private fun open(
        key: ByteArray,
        role: Int,
        highest: Long,
        bitmap: Long,
        frame: ByteArray,
        context: ByteArray = CONTEXT,
    ): Opened? {
        val out = CoreCrypto.mediaOpen(key, role, context, highest, bitmap, frame) ?: return null
        assertTrue(out.size > 16)
        val buf = ByteBuffer.wrap(out)
        return Opened(buf.getLong(0), buf.getLong(8), out.copyOfRange(16, out.size))
    }

    private fun ByteArray.with(index: Int, value: Int) = copyOf().also { it[index] = value.toByte() }

    private companion object {
        const val CALLER = 1
        const val CALLEE = 2
        const val HEADER = 44
        const val TAG = 16
        const val MAX_PLAINTEXT = 4000
        val CALLER_KEY = ByteArray(32) { 0x11 }
        val CALLEE_KEY = ByteArray(32) { 0x22 }
        val CONTEXT = ByteArray(32) { (it * 7 + 3).toByte() }
        val PAYLOAD = "synthetic-opus-frame-v3".toByteArray()
    }
}

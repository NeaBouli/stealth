package com.securecall.app.crypto

/**
 * Narrow AEAD boundary for STX-24 media frames. Production uses the Rust
 * XChaCha20-Poly1305 implementation through [NativeMediaFrameCipher].
 */
internal interface MediaFrameCipher {
    fun seal(key: ByteArray, senderRole: Int, context: ByteArray, sequence: Long, plaintext: ByteArray): ByteArray?

    /** Returns [newHighest(8 BE) | newBitmap(8 BE) | plaintext] for a fresh authenticated frame, else null. */
    fun open(
        key: ByteArray, expectedPeerRole: Int, context: ByteArray,
        highest: Long, bitmap: Long, frame: ByteArray,
    ): ByteArray?
}

internal object NativeMediaFrameCipher : MediaFrameCipher {
    override fun seal(key: ByteArray, senderRole: Int, context: ByteArray, sequence: Long, plaintext: ByteArray) =
        com.securecall.crypto.CoreCrypto.mediaSeal(key, senderRole, context, sequence, plaintext)

    override fun open(
        key: ByteArray, expectedPeerRole: Int, context: ByteArray,
        highest: Long, bitmap: Long, frame: ByteArray,
    ) = com.securecall.crypto.CoreCrypto.mediaOpen(key, expectedPeerRole, context, highest, bitmap, frame)
}

/**
 * Per-call STX-24 media state: both direction keys, the outbound sequence and the
 * inbound replay window. One instance lives for exactly one authenticated call key;
 * WebRTC reconnects and ICE restarts reuse it, so a sequence never repeats under a key.
 * [close] clears keys and replay state together. All operations are serialized, the
 * replay state is committed only after the frame authenticated, and [consume] keeps
 * decode/enqueue inside the same boundary as [close].
 */
class MediaSessionV3 internal constructor(
    val sessionId: String,
    localIsCaller: Boolean,
    keys: CallKeysV3,
    private val cipher: MediaFrameCipher,
    firstSequence: Long = 1L,
) {
    private val lock = Any()
    private val localRole = if (localIsCaller) ROLE_CALLER else ROLE_CALLEE
    private val peerRole = if (localIsCaller) ROLE_CALLEE else ROLE_CALLER
    private var txKey: ByteArray? = (if (localIsCaller) keys.callerToCallee else keys.calleeToCaller).copyOf()
    private var rxKey: ByteArray? = (if (localIsCaller) keys.calleeToCaller else keys.callerToCallee).copyOf()
    private val context: ByteArray = keys.context.copyOf()
    private var nextSequence = firstSequence
    private var highest = 0L
    private var bitmap = 0L
    private var closed = false

    constructor(sessionId: String, localIsCaller: Boolean, keys: CallKeysV3) :
        this(sessionId, localIsCaller, keys, NativeMediaFrameCipher)

    val isClosed: Boolean get() = synchronized(lock) { closed }

    fun seal(plaintext: ByteArray): ByteArray? = synchronized(lock) {
        val key = txKey ?: return null
        if (closed || plaintext.isEmpty() || plaintext.size > MAX_PLAINTEXT) return null
        // Fail closed at exhaustion; a sequence is consumed even if sealing fails.
        val sequence = nextSequence
        if (sequence < 1L) return null
        nextSequence = if (sequence == Long.MAX_VALUE) EXHAUSTED else sequence + 1
        cipher.seal(key, localRole, context, sequence, plaintext)
    }

    /**
     * Authenticates [frame] and hands its plaintext to [consumer] inside the same
     * critical section as the replay commit and [close]. The consumer runs at most once
     * per authenticated frame, never after [close] has returned, and the plaintext is
     * wiped when it returns. The consumer must not take locks that a [close] caller holds.
     * Returns true only if the frame authenticated and was consumed.
     */
    fun consume(frame: ByteArray, consumer: (ByteArray) -> Unit): Boolean = synchronized(lock) {
        val key = rxKey ?: return false
        if (closed || frame.size < MIN_FRAME || frame.size > MAX_FRAME) return false
        val out = cipher.open(key, peerRole, context, highest, bitmap, frame) ?: return false
        if (out.size <= STATE_SIZE) {
            out.fill(0)
            return false
        }
        val newHighest = readLong(out, 0)
        val newBitmap = readLong(out, 8)
        if (newHighest < highest || newHighest < 1L) {
            out.fill(0)
            return false
        }
        // Authenticated: commit the replay state before the payload is used.
        highest = newHighest
        bitmap = newBitmap
        val plaintext = out.copyOfRange(STATE_SIZE, out.size)
        out.fill(0)
        try {
            consumer(plaintext)
        } finally {
            plaintext.fill(0)
        }
        true
    }

    /** Waits for an in-flight [consume]; afterwards no consumer runs and keys are wiped. */
    fun close() = synchronized(lock) {
        closed = true
        txKey?.fill(0)
        rxKey?.fill(0)
        txKey = null
        rxKey = null
        context.fill(0)
        nextSequence = EXHAUSTED
        highest = 0L
        bitmap = 0L
    }

    private fun readLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (bytes[offset + i].toLong() and 0xff)
        return value
    }

    companion object {
        const val ROLE_CALLER = 1
        const val ROLE_CALLEE = 2
        // Must match core_crypto::aead MEDIA_* constants.
        const val HEADER_SIZE = 44
        const val TAG_SIZE = 16
        // native-lib.cpp nativeEncode writes at most MAX_PACKET = 4000 bytes.
        const val MAX_PLAINTEXT = 4000
        const val MIN_FRAME = HEADER_SIZE + 1 + TAG_SIZE
        const val MAX_FRAME = HEADER_SIZE + MAX_PLAINTEXT + TAG_SIZE
        private const val STATE_SIZE = 16
        private const val EXHAUSTED = -1L
    }
}

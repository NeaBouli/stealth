package com.securecall.app.net

import android.util.Log
import com.securecall.app.audio.jitter.JitterBuffer

/**
 * Owned-test media data-path receipt (SC-OWNED-MEDIA-RECEIPT-20261119).
 *
 * Exists only in an owned-test debug build ([createIfActive] returns null otherwise, so
 * production allocates and tracks nothing). Counts an authenticated, decoded PCM frame only
 * when that exact array (identity, not content) was marked by its immutable media owner
 * before enqueue, then dequeued and written with a positive captured AudioTrack result.
 * Holds at most [JitterBuffer.MAX_SIZE] queued arrays plus one explicitly dequeued array;
 * never logs identifiers, frame contents, keys or addresses. Leaf lock: callers may hold the
 * media or audio lock; only fixed Android logging runs under this leaf lock, never media/audio.
 */
internal class OwnedMediaReceipt private constructor() {

    data class Snapshot(val consumed: Int, val partial: Int, val pending: Int, val inFlight: Int = 0)

    private val lock = Any()
    private var owner: Any? = null
    private val pending = ArrayDeque<ShortArray>()
    private var inFlight: ShortArray? = null
    private var consumed = 0
    private var partial = 0

    /** Marks the exact decoded PCM about to be enqueued by the authenticated consumer of [frameOwner]. */
    fun decoded(frameOwner: Any, pcm: ShortArray) {
        synchronized(lock) {
            if (frameOwner !== owner) {
                resetLocked()
                owner = frameOwner
            }
            // Only queue-resident marks participate in actual jitter overflow.
            if (pending.size >= JitterBuffer.MAX_SIZE) pending.removeFirst()
            pending.addLast(pcm)
        }
    }

    /** Called at the actual pop seam, before any blocking write; paused frames are discarded. */
    fun dequeued(pcm: ShortArray, playable: Boolean) {
        synchronized(lock) {
            val index = pending.indexOfFirst { it === pcm }
            if (index < 0) return
            repeat(index + 1) { pending.removeFirst() }
            if (playable) inFlight = pcm
        }
    }

    /** Records the captured AudioTrack result for a dequeued array; unmarked arrays are ignored. */
    fun written(pcm: ShortArray, wrote: Int) {
        synchronized(lock) {
            if (inFlight !== pcm) return
            inFlight = null
            if (wrote <= 0) return
            consumed++
            if (wrote < pcm.size) partial++
            if (consumed == 1 || consumed % LOG_EVERY == 0) log("progress", snapshotLocked())
        }
    }

    /** Clears ownership and emits the captured final counts as one reset-ordered operation. */
    fun reset() {
        synchronized(lock) {
            val last = snapshotLocked()
            resetLocked()
            if (last.consumed > 0) log("final", last)
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) { snapshotLocked() }

    private fun snapshotLocked() = Snapshot(consumed, partial, pending.size, if (inFlight == null) 0 else 1)

    private fun resetLocked() {
        owner = null
        pending.clear()
        inFlight = null
        consumed = 0
        partial = 0
    }

    private fun log(phase: String, snapshot: Snapshot) {
        Log.i(TAG, "owned media receipt $phase: consumed=${snapshot.consumed} partial=${snapshot.partial}")
    }

    companion object {
        private const val TAG = "OWNED_MEDIA_RECEIPT"
        private const val LOG_EVERY = 250 // about 5 s of 20 ms frames

        /** Same activation as owned ICE: debug build AND nonempty owned host, else null. */
        fun createIfActive(debug: Boolean, configuredOwnedHost: String): OwnedMediaReceipt? =
            if (OwnedIcePolicy.ownedHost(debug, configuredOwnedHost).isNotEmpty()) OwnedMediaReceipt() else null
    }
}

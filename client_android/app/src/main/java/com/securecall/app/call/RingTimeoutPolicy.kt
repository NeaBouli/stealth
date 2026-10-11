package com.securecall.app.call

/**
 * Outgoing calls end after [OUTGOING_RING_TIMEOUT_MS] without an authenticated accept.
 * The callee's own 60 s timer only runs when its full-screen activity opens; on an unlocked
 * Android 14+ device the call may stay a heads-up notification, so without a caller-side
 * limit both phones could ring indefinitely (#136 API 34 QA, 2026-10-11).
 */
object RingTimeoutPolicy {
    const val OUTGOING_RING_TIMEOUT_MS = 60_000L

    /** Expire only the still-ringing outgoing session the timer was started for. */
    fun shouldExpire(isCaller: Boolean, sameSession: Boolean, accepted: Boolean): Boolean =
        isCaller && sameSession && !accepted
}

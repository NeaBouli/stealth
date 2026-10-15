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

/**
 * A remote CALL_END must release the per-call security state of exactly that session.
 * Otherwise a missed call (caller hung up or timed out while the callee only saw a
 * notification) leaves the old invite behind and every later invite is rejected as
 * conflicting until the app restarts (#136 real-device QA, 2026-10-12).
 */
object CallEndPolicy {
    fun releasesSecurityState(stateSessionId: String?, endedSessionId: String): Boolean =
        stateSessionId != null && stateSessionId == endedSessionId
}

/**
 * Incoming-call UI launch. With the screen off (doze / always-on display) a direct
 * startActivity() neither wakes the device nor beats the keyguard, and because the activity
 * then already exists the full-screen-intent notification no longer wakes the screen either:
 * the call rings behind a dark screen (#136 Tab S4 QA, 2026-10-12). Only launch directly while
 * the device is interactive; otherwise the system launches it via the full-screen intent.
 */
object IncomingLaunchPolicy {
    fun launchDirectly(screenInteractive: Boolean, alreadyShowing: Boolean): Boolean =
        screenInteractive && !alreadyShowing
}

package com.securecall.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Caller-side ring timeout: only an unanswered outgoing session of this caller expires. */
class RingTimeoutPolicyTest {
    @Test
    fun unansweredOutgoingSessionExpires() {
        assertTrue(RingTimeoutPolicy.shouldExpire(isCaller = true, sameSession = true, accepted = false))
    }

    @Test
    fun acceptedCallIsNeverEnded() {
        assertFalse(RingTimeoutPolicy.shouldExpire(isCaller = true, sameSession = true, accepted = true))
    }

    @Test
    fun laterOrForeignSessionIsNotEnded() {
        assertFalse(RingTimeoutPolicy.shouldExpire(isCaller = true, sameSession = false, accepted = false))
        assertFalse(RingTimeoutPolicy.shouldExpire(isCaller = false, sameSession = true, accepted = false))
    }

    @Test
    fun timeoutMatchesCalleeRingWindow() {
        assertEquals(60_000L, RingTimeoutPolicy.OUTGOING_RING_TIMEOUT_MS)
    }
}

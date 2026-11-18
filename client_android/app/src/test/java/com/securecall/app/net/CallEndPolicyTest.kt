package com.securecall.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEndPolicyTest {
    @Test
    fun `explicit peer disconnect receives recovery grace`() {
        assertTrue(CallEndPolicy.shouldDelay("peer_disconnected"))
    }

    @Test
    fun `user hangup ends immediately`() {
        assertFalse(CallEndPolicy.shouldDelay("user_hangup"))
    }

    @Test
    fun `missing legacy reason ends immediately`() {
        assertFalse(CallEndPolicy.shouldDelay(""))
    }

    @Test
    fun `stale end does not apply to a newer active call`() {
        assertFalse(CallEndPolicy.isRelevant("old", "new", "new", null))
    }

    @Test
    fun `matching active call end applies`() {
        assertTrue(CallEndPolicy.isRelevant("s1", "s1", "s1", null))
    }

    @Test
    fun `pending security session end applies after current session was cleared`() {
        assertTrue(CallEndPolicy.isRelevant("s1", null, "s1", null))
    }

    @Test
    fun `fcm pending invite end applies for caller cancel during ringing`() {
        assertTrue(CallEndPolicy.isRelevant("s1", null, null, "s1"))
    }

    @Test
    fun `empty or unknown end never applies`() {
        assertFalse(CallEndPolicy.isRelevant("", "s1", "s1", "s1"))
        assertFalse(CallEndPolicy.isRelevant("", null, null, null))
        assertFalse(CallEndPolicy.isRelevant("x", null, null, null))
    }

    @Test
    fun `upgrade callback only for relevant allowlisted end`() {
        assertTrue(CallEndPolicy.reportsUpgradeRequired("media_upgrade_required", true))
        assertFalse(CallEndPolicy.reportsUpgradeRequired("media_upgrade_required", false))
        assertFalse(CallEndPolicy.reportsUpgradeRequired("user_hangup", true))
    }

    @Test
    fun `stale peer disconnect cannot start grace for a newer call`() {
        // handleIncomingCallEnd checks relevance before shouldDelay/grace scheduling.
        val relevant = CallEndPolicy.isRelevant("old", "new", "new", null)
        assertFalse(relevant && CallEndPolicy.shouldDelay("peer_disconnected"))
    }
}

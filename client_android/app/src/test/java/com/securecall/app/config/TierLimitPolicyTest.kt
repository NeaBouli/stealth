package com.securecall.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TierLimitPolicy] — the centralized Free-tier limit rules.
 * Values are passed explicitly, so these tests are flavor-independent.
 */
class TierLimitPolicyTest {

    // ── Contact limit: boundaries ────────────────────────────────────────

    @Test
    fun `first contact is allowed under a positive limit`() {
        assertTrue(TierLimitPolicy.canAddContact(currentCount = 0, maxContacts = 10))
    }

    @Test
    fun `tenth of ten contacts is allowed`() {
        // 9 stored → adding the 10th must succeed
        assertTrue(TierLimitPolicy.canAddContact(currentCount = 9, maxContacts = 10))
    }

    @Test
    fun `eleventh contact is rejected at the free limit`() {
        // 10 stored → adding the 11th must be rejected
        assertFalse(TierLimitPolicy.canAddContact(currentCount = 10, maxContacts = 10))
    }

    @Test
    fun `count above the limit is rejected`() {
        assertFalse(TierLimitPolicy.canAddContact(currentCount = 25, maxContacts = 10))
    }

    @Test
    fun `limit of one allows exactly one contact`() {
        assertTrue(TierLimitPolicy.canAddContact(currentCount = 0, maxContacts = 1))
        assertFalse(TierLimitPolicy.canAddContact(currentCount = 1, maxContacts = 1))
    }

    // ── Contact limit: unlimited and invalid values ──────────────────────

    @Test
    fun `zero max contacts means unlimited`() {
        assertTrue(TierLimitPolicy.canAddContact(currentCount = 0, maxContacts = 0))
        assertTrue(TierLimitPolicy.canAddContact(currentCount = 10_000, maxContacts = 0))
    }

    @Test
    fun `negative max contacts is invalid and treated as unlimited`() {
        assertTrue(TierLimitPolicy.canAddContact(currentCount = 10_000, maxContacts = -1))
    }

    @Test
    fun `negative current count is coerced and cannot hard-block`() {
        assertTrue(TierLimitPolicy.canAddContact(currentCount = -3, maxContacts = 10))
    }

    // ── Call duration limit ──────────────────────────────────────────────

    @Test
    fun `free tier fifteen minutes converts to milliseconds`() {
        assertEquals(15 * 60_000L, TierLimitPolicy.callDurationLimitMs(15))
    }

    @Test
    fun `one minute boundary converts to milliseconds`() {
        assertEquals(60_000L, TierLimitPolicy.callDurationLimitMs(1))
    }

    @Test
    fun `zero minutes means no call limit`() {
        assertEquals(0L, TierLimitPolicy.callDurationLimitMs(0))
    }

    @Test
    fun `negative minutes is invalid and means no call limit`() {
        assertEquals(0L, TierLimitPolicy.callDurationLimitMs(-5))
    }

    @Test
    fun `huge minute values do not overflow`() {
        val result = TierLimitPolicy.callDurationLimitMs(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE.toLong() * 60_000L, result)
        assertTrue(result > 0L)
    }

    // ── Deterministic user-visible messages ──────────────────────────────

    @Test
    fun `contact limit message contains the configured limit`() {
        val msg = TierLimitPolicy.contactLimitMessage(10)
        assertTrue(msg.contains("10"))
        assertFalse(msg.isBlank())
    }

    @Test
    fun `call limit message contains the configured minutes`() {
        val msg = TierLimitPolicy.callLimitMessage(15)
        assertTrue(msg.contains("15"))
        assertFalse(msg.isBlank())
    }
}

package com.securecall.app.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionTierTest {
    @Test
    fun `server tier names are case insensitive`() {
        assertEquals(SubscriptionTier.PRO, SubscriptionTier.fromName("pro"))
        assertEquals(SubscriptionTier.PREMIUM, SubscriptionTier.fromName("premium"))
        assertEquals(SubscriptionTier.FREE, SubscriptionTier.fromName("FREE"))
    }

    @Test
    fun `unknown server tier fails closed to free`() {
        assertEquals(SubscriptionTier.FREE, SubscriptionTier.fromName("enterprise"))
    }
}

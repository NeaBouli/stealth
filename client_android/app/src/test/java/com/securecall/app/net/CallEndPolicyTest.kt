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
}

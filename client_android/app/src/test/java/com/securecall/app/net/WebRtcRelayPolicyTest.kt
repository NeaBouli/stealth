package com.securecall.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcRelayPolicyTest {

    @Test
    fun directTransportWhenVpnAndRetryAreInactive() {
        assertFalse(WebRtcManager.shouldUseRelayOnly(externalVpnActive = false, relayRetry = false))
    }

    @Test
    fun externalVpnForcesRelayOnly() {
        assertTrue(WebRtcManager.shouldUseRelayOnly(externalVpnActive = true, relayRetry = false))
    }

    @Test
    fun retryForcesRelayOnlyWithoutVpn() {
        assertTrue(WebRtcManager.shouldUseRelayOnly(externalVpnActive = false, relayRetry = true))
    }
}

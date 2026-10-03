package com.securecall.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.PeerConnection

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

    @Test
    fun directModeUsesConfiguredStunFallbackWhenDynamicServersAreMissing() {
        assertEquals(
            WebRtcManager.IceSourceDecision.STUN_FALLBACK,
            WebRtcManager.decideIceSource(dynamicIceServers = null, relayOnly = false)
        )
    }

    @Test
    fun relayOnlyRejectsMissingOrStunOnlyDynamicServers() {
        val stunOnly = listOf(PeerConnection.IceServer.builder("stun:stun.example.test:3478").createIceServer())
        assertEquals(
            WebRtcManager.IceSourceDecision.REJECT,
            WebRtcManager.decideIceSource(dynamicIceServers = null, relayOnly = true)
        )
        assertEquals(
            WebRtcManager.IceSourceDecision.REJECT,
            WebRtcManager.decideIceSource(dynamicIceServers = stunOnly, relayOnly = true)
        )
    }

    @Test
    fun relayOnlyAcceptsDynamicallySuppliedTurnServer() {
        val supplied = listOf(PeerConnection.IceServer.builder("turns:turn.example.test:443").createIceServer())
        assertEquals(
            WebRtcManager.IceSourceDecision.DYNAMIC,
            WebRtcManager.decideIceSource(dynamicIceServers = supplied, relayOnly = true)
        )
    }
}

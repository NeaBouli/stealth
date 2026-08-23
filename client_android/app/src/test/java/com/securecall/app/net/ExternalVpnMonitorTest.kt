package com.securecall.app.net

import com.securecall.app.net.ExternalVpnMonitor.VpnEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure state-decision tests for [ExternalVpnMonitor]. Deterministic: no
 * Android framework, no threads, no randomness.
 */
class ExternalVpnMonitorTest {

    @Test
    fun `capabilities with vpn transport activate the indicator`() {
        assertTrue(ExternalVpnMonitor.reduceVpnActive(VpnEvent.CAPABILITIES, hasVpnTransport = true))
    }

    @Test
    fun `capabilities without vpn transport deactivate the indicator`() {
        assertFalse(ExternalVpnMonitor.reduceVpnActive(VpnEvent.CAPABILITIES, hasVpnTransport = false))
    }

    @Test
    fun `stop always clears active state so no stale protected state remains`() {
        assertFalse(ExternalVpnMonitor.reduceVpnActive(VpnEvent.STOP, hasVpnTransport = false))
    }

    @Test
    fun `vpn loss after vpn active ends in inactive state`() {
        // Full deterministic sequence: VPN appears, then drops.
        val active = ExternalVpnMonitor.reduceVpnActive(VpnEvent.CAPABILITIES, hasVpnTransport = true)
        assertTrue(active)
        val inactive = ExternalVpnMonitor.reduceVpnActive(VpnEvent.CAPABILITIES, hasVpnTransport = false)
        assertFalse(inactive)
    }
}

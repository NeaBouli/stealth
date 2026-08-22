package com.securecall.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure animation-decision tests for [VpnStatusIndicatorView]. Deterministic:
 * no Android framework, no views, no animator threads.
 */
class VpnStatusIndicatorViewTest {

    @Test
    fun `pulse runs while system animations are enabled`() {
        assertTrue(VpnStatusIndicatorView.shouldPulse(1.0f))
        assertTrue(VpnStatusIndicatorView.shouldPulse(0.5f))
    }

    @Test
    fun `pulse is suppressed when system animations are disabled`() {
        // Animations off → static, fully visible green LED.
        assertFalse(VpnStatusIndicatorView.shouldPulse(0.0f))
    }
}

package com.securecall.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boundary tests for [ForegroundServicePolicy] (Android 15 = API 35).
 * API 24–34 keep the legacy persistent behavior; API 35+ must not.
 */
class ForegroundServicePolicyTest {

    @Test
    fun api34_persistentIdleSignalingAllowed() {
        assertTrue(ForegroundServicePolicy.allowsPersistentIdleSignaling(34))
    }

    @Test
    fun api35_persistentIdleSignalingDenied() {
        assertFalse(ForegroundServicePolicy.allowsPersistentIdleSignaling(35))
    }

    @Test
    fun api36_persistentIdleSignalingDenied() {
        assertFalse(ForegroundServicePolicy.allowsPersistentIdleSignaling(36))
    }

    @Test
    fun api34_bootStartAllowed() {
        assertTrue(ForegroundServicePolicy.allowsBootStart(34))
    }

    @Test
    fun api35_bootStartDenied() {
        assertFalse(ForegroundServicePolicy.allowsBootStart(35))
    }

    @Test
    fun api36_bootStartDenied() {
        assertFalse(ForegroundServicePolicy.allowsBootStart(36))
    }

    @Test
    fun api34_keepAliveAllowed() {
        assertTrue(ForegroundServicePolicy.allowsKeepAlive(34))
    }

    @Test
    fun api35_keepAliveDenied() {
        assertFalse(ForegroundServicePolicy.allowsKeepAlive(35))
    }

    @Test
    fun api36_keepAliveDenied() {
        assertFalse(ForegroundServicePolicy.allowsKeepAlive(36))
    }

    @Test
    fun minSdk24_allLegacyBehaviorsAllowed() {
        assertTrue(ForegroundServicePolicy.allowsPersistentIdleSignaling(24))
        assertTrue(ForegroundServicePolicy.allowsBootStart(24))
        assertTrue(ForegroundServicePolicy.allowsKeepAlive(24))
    }

    @Test
    fun api34_batteryOptimizationExemptionAllowed() {
        assertTrue(ForegroundServicePolicy.shouldRequestBatteryOptimizationExemption(34))
    }

    @Test
    fun api35_batteryOptimizationExemptionDenied() {
        assertFalse(ForegroundServicePolicy.shouldRequestBatteryOptimizationExemption(35))
    }
}

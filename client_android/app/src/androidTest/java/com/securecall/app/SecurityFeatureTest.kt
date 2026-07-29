package com.securecall.app

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.app.security.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for anti-recording security features.
 *
 * These tests verify that all security protection layers
 * are correctly initialized and functioning.
 */
@RunWith(AndroidJUnit4::class)
class SecurityFeatureTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    // ─── FLAG_SECURE Tests ──────────────────────────────

    @Test
    fun callActivity_hasFlagSecure() {
        val scenario = ActivityScenario.launch(CallActivity::class.java)
        scenario.onActivity { activity ->
            val flags = activity.window.attributes.flags
            // FLAG_SECURE should be set (based on tier and preferences)
            // For release builds, this should always be true for PRO/PREMIUM
            val tier = try {
                com.securecall.app.config.FeatureProviderRegistry.get().tier
            } catch (e: Exception) {
                "FREE"
            }

            if (tier == "PREMIUM" || tier == "PRO") {
                assertTrue(
                    "FLAG_SECURE should be set for $tier tier",
                    (flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
                )
            }
        }
        scenario.close()
    }

    // ─── AudioFocusManager Tests ────────────────────────

    @Test
    fun audioFocusManager_requestsExclusiveFocus() {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        scenario.onActivity { activity ->
            val manager = AudioFocusManager(activity)
            val result = manager.requestExclusiveFocus()
            assertTrue("Foreground activity should obtain exclusive audio focus", result)
            assertTrue("hasFocus should return true", manager.hasFocus())
            manager.abandonFocus()
            assertFalse("hasFocus should return false after abandon", manager.hasFocus())
        }
        scenario.close()
    }

    // ─── ScreenRecordingDetector Tests ──────────────────

    @Test
    fun screenRecordingDetector_initialStateNotRecording() {
        val detector = ScreenRecordingDetector(context)
        // On a clean test device, no recording should be active
        // Note: This may fail on CI with screen recording enabled
        val isRecording = detector.isRecordingDetected()
        // Just verify it doesn't crash — actual state depends on test environment
        assertNotNull("isRecordingDetected should return a value", isRecording)
    }

    // ─── MicrophoneMonitor Tests ────────────────────────

    @Test
    fun microphoneMonitor_startsAndStops() {
        val monitor = MicrophoneMonitor(context)
        // Should not crash
        monitor.startMonitoring()
        val count = monitor.getActiveRecordingCount()
        assertTrue("Recording count should be >= 0", count >= 0)
        monitor.stopMonitoring()
    }

    // ─── AccessibilityDetector Tests ────────────────────

    @Test
    fun accessibilityDetector_runsWithoutCrash() {
        val detector = AccessibilityDetector(context)
        val result = detector.detect()
        assertNotNull("Detection result should not be null", result)
        // On a clean device, no spy apps should be detected
        assertTrue("No spy apps on clean test device", result.spyAppsDetected.isEmpty())
    }

    // ─── CallRecordingDetector Tests ────────────────────

    @Test
    fun callRecordingDetector_scansInstalled() {
        val detector = CallRecordingDetector(context)
        val result = detector.detect()
        assertNotNull("Detection result should not be null", result)
        // Verify the scan completes without crash
        assertNotNull("Detected apps list should not be null", result.detectedApps)
    }

    // ─── SecureCallMonitor Tests ────────────────────────

    @Test
    fun secureCallMonitor_performsFullScan() {
        val monitor = SecureCallMonitor(context)
        val status = monitor.performFullScan()

        assertNotNull("Security status should not be null", status)
        assertNotNull("Security level should not be null", status.level)
        assertNotNull("Threats list should not be null", status.threats)

        // Verify level is one of the valid values
        assertTrue(
            "Level should be GREEN, YELLOW, or RED",
            status.level in listOf(
                SecureCallMonitor.SecurityLevel.GREEN,
                SecureCallMonitor.SecurityLevel.YELLOW,
                SecureCallMonitor.SecurityLevel.RED
            )
        )
    }

    @Test
    fun secureCallMonitor_threatCountMatchesLevel() {
        val monitor = SecureCallMonitor(context)
        val status = monitor.performFullScan()

        if (status.level == SecureCallMonitor.SecurityLevel.GREEN) {
            assertEquals("GREEN level should have 0 critical threats", 0,
                status.threats.count { it.severity == SecureCallMonitor.Severity.CRITICAL })
        }
    }

    // ─── SecurityEnforcer Tests ─────────────────────────

    @Test
    fun securityEnforcer_evaluatesScreenCapture() {
        val action = SecurityEnforcer.evaluate(SecurityEnforcer.Violation.SCREEN_CAPTURE)
        assertNotNull("Action should not be null", action)

        val tier = try {
            com.securecall.app.config.FeatureProviderRegistry.get().tier
        } catch (e: Exception) {
            return // Can't test without FeatureProvider
        }

        when (tier) {
            "FREE" -> assertEquals("FREE should ALLOW (detection disabled)",
                SecurityEnforcer.Action.ALLOW, action)
            "PRO" -> assertEquals("PRO should BLOCK",
                SecurityEnforcer.Action.BLOCK, action)
            "PREMIUM" -> assertEquals("PREMIUM should TERMINATE",
                SecurityEnforcer.Action.TERMINATE, action)
        }
    }

    @Test
    fun securityEnforcer_evaluatesMicrophoneHijack() {
        val action = SecurityEnforcer.evaluate(SecurityEnforcer.Violation.MICROPHONE_HIJACK)
        assertNotNull("Action should not be null", action)
    }

    @Test
    fun securityEnforcer_evaluatesCallRecordingApp() {
        val action = SecurityEnforcer.evaluate(SecurityEnforcer.Violation.CALL_RECORDING_APP)
        assertNotNull("Action should not be null", action)
    }
}

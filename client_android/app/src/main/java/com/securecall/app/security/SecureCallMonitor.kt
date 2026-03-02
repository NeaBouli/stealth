package com.securecall.app.security

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.securecall.app.config.FeatureProviderRegistry

/**
 * Central security monitor for active calls.
 *
 * Orchestrates all anti-recording detection systems and enforces
 * tier-based security policies during encrypted voice calls.
 *
 * Security levels:
 * - GREEN: All checks passed, call is secure
 * - YELLOW: Warnings detected (non-critical)
 * - RED: Critical security threats detected
 *
 * Tier enforcement:
 * - FREE:    Warnings only (toasts)
 * - PRO:     Block call on critical issues (dialog)
 * - PREMIUM: Terminate app immediately on any threat
 */
class SecureCallMonitor(private val context: Context) {

    private val TAG = "SecureCallMonitor"

    // ─── Sub-monitors ───────────────────────────────────
    val audioFocusManager = AudioFocusManager(context)
    val screenRecordingDetector = ScreenRecordingDetector(context)
    val microphoneMonitor = MicrophoneMonitor(context)
    val accessibilityDetector = AccessibilityDetector(context)
    val callRecordingDetector = CallRecordingDetector(context)

    private val handler = Handler(Looper.getMainLooper())
    private var periodicCheckRunnable: Runnable? = null
    private var isMonitoring = false

    // ─── Security Status ────────────────────────────────
    enum class SecurityLevel {
        GREEN,   // All clear
        YELLOW,  // Warnings
        RED      // Critical threats
    }

    data class SecurityStatus(
        val level: SecurityLevel,
        val threats: List<Threat>,
        val flagSecureActive: Boolean,
        val exclusiveAudioFocus: Boolean
    ) {
        val isSafe: Boolean get() = level == SecurityLevel.GREEN
        val threatCount: Int get() = threats.size
    }

    data class Threat(
        val type: ThreatType,
        val severity: Severity,
        val description: String,
        val details: List<String> = emptyList()
    )

    enum class ThreatType {
        SCREEN_RECORDING,
        DISPLAY_CAPTURE,
        MICROPHONE_HIJACK,
        SPY_APP_ACCESSIBILITY,
        SPY_APP_INSTALLED,
        SUSPICIOUS_NOTIFICATION_LISTENER,
        CALL_RECORDING_APP,
        AUDIO_FOCUS_LOST
    }

    enum class Severity {
        WARNING,   // FREE: toast
        CRITICAL   // PRO: dialog, PREMIUM: terminate
    }

    // ─── Callbacks ──────────────────────────────────────
    var onSecurityStatusChanged: ((SecurityStatus) -> Unit)? = null
    var onCriticalThreat: ((Threat) -> Unit)? = null

    /**
     * Run all security checks before/during a call.
     * Returns the current security status.
     */
    fun performFullScan(): SecurityStatus {
        val threats = mutableListOf<Threat>()

        // 1. Screen Recording Detection
        if (screenRecordingDetector.isRecordingDetected()) {
            threats.add(Threat(
                type = ThreatType.SCREEN_RECORDING,
                severity = Severity.CRITICAL,
                description = "Screen recording is active"
            ))
        }

        // 2. Display Capture (virtual displays)
        if (screenRecordingDetector.isDisplayCaptured()) {
            threats.add(Threat(
                type = ThreatType.DISPLAY_CAPTURE,
                severity = Severity.CRITICAL,
                description = "Display is being captured"
            ))
        }

        // 3. Microphone Hijack
        if (microphoneMonitor.isOtherAppRecording()) {
            threats.add(Threat(
                type = ThreatType.MICROPHONE_HIJACK,
                severity = Severity.CRITICAL,
                description = "Another app is recording audio",
                details = listOf("Active recording sessions: ${microphoneMonitor.getActiveRecordingCount()}")
            ))
        }

        // 4. Accessibility Service Detection
        val accessResult = accessibilityDetector.detect()
        if (accessResult.spyAppsDetected.isNotEmpty()) {
            threats.add(Threat(
                type = ThreatType.SPY_APP_ACCESSIBILITY,
                severity = Severity.CRITICAL,
                description = "Spy app detected with accessibility access",
                details = accessResult.spyAppsDetected
            ))
        }
        if (accessResult.suspiciousServices.isNotEmpty()) {
            threats.add(Threat(
                type = ThreatType.SPY_APP_ACCESSIBILITY,
                severity = Severity.WARNING,
                description = "Suspicious accessibility services active",
                details = accessResult.suspiciousServices
            ))
        }
        if (accessResult.suspiciousNotificationListeners.isNotEmpty()) {
            threats.add(Threat(
                type = ThreatType.SUSPICIOUS_NOTIFICATION_LISTENER,
                severity = Severity.WARNING,
                description = "Suspicious apps have notification access",
                details = accessResult.suspiciousNotificationListeners
            ))
        }

        // 5. Call Recording Apps
        val recordResult = callRecordingDetector.detect()
        if (recordResult.hasRecordingApps) {
            val appNames = recordResult.detectedApps.map { it.appName }
            threats.add(Threat(
                type = ThreatType.CALL_RECORDING_APP,
                severity = if (recordResult.detectedApps.any { it.isKnown }) Severity.CRITICAL else Severity.WARNING,
                description = "Call recording app(s) installed",
                details = appNames
            ))
        }

        // 6. Audio Focus — retry before warning
        if (!audioFocusManager.hasFocus()) {
            audioFocusManager.requestExclusiveFocus()
            if (!audioFocusManager.hasFocus()) {
                threats.add(Threat(
                    type = ThreatType.AUDIO_FOCUS_LOST,
                    severity = Severity.WARNING,
                    description = "Exclusive audio focus not held"
                ))
            }
        }

        // Determine overall security level
        val level = when {
            threats.any { it.severity == Severity.CRITICAL } -> SecurityLevel.RED
            threats.isNotEmpty() -> SecurityLevel.YELLOW
            else -> SecurityLevel.GREEN
        }

        val status = SecurityStatus(
            level = level,
            threats = threats,
            flagSecureActive = true, // Set by CallActivity
            exclusiveAudioFocus = audioFocusManager.hasFocus()
        )

        Log.d(TAG, "Security scan complete: level=$level, threats=${threats.size}")
        return status
    }

    /**
     * Start continuous monitoring during an active call.
     * Runs periodic checks every 5 seconds.
     */
    fun startContinuousMonitoring(activity: Activity) {
        isMonitoring = true

        // Start sub-monitors
        audioFocusManager.requestExclusiveFocus()
        screenRecordingDetector.startMonitoring(activity)
        microphoneMonitor.startMonitoring()

        // Set up real-time callbacks
        audioFocusManager.onFocusLost = {
            Log.w(TAG, "Audio focus lost during call!")
            val status = performFullScan()
            onSecurityStatusChanged?.invoke(status)
        }

        screenRecordingDetector.onRecordingStateChanged = { isRecording ->
            if (isRecording) {
                Log.e(TAG, "Screen recording detected during call!")
                val threat = Threat(
                    type = ThreatType.SCREEN_RECORDING,
                    severity = Severity.CRITICAL,
                    description = "Screen recording started during call"
                )
                onCriticalThreat?.invoke(threat)
                enforceTierPolicy(threat)
            }
            val status = performFullScan()
            onSecurityStatusChanged?.invoke(status)
        }

        microphoneMonitor.onOtherAppRecording = { isRecording, details ->
            if (isRecording) {
                Log.e(TAG, "Other app recording detected during call!")
                val threat = Threat(
                    type = ThreatType.MICROPHONE_HIJACK,
                    severity = Severity.CRITICAL,
                    description = "Another app started recording",
                    details = details
                )
                onCriticalThreat?.invoke(threat)
                enforceTierPolicy(threat)
            }
        }

        // Periodic full scan every 5 seconds
        periodicCheckRunnable = object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    val status = performFullScan()
                    onSecurityStatusChanged?.invoke(status)

                    // Enforce on critical threats
                    status.threats.filter { it.severity == Severity.CRITICAL }.forEach { threat ->
                        enforceTierPolicy(threat)
                    }

                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                }
            }
        }
        handler.postDelayed(periodicCheckRunnable!!, CHECK_INTERVAL_MS)

        // Initial scan
        val initialStatus = performFullScan()
        onSecurityStatusChanged?.invoke(initialStatus)

        Log.d(TAG, "Continuous monitoring started")
    }

    /**
     * Enforce security policy based on current tier.
     */
    private fun enforceTierPolicy(threat: Threat) {
        val action = SecurityEnforcer.evaluate(SecurityEnforcer.Violation.SCREEN_CAPTURE)

        when (action) {
            SecurityEnforcer.Action.ALLOW -> {
                // FREE with detection disabled — no-op
            }
            SecurityEnforcer.Action.WARN -> {
                // FREE tier: just warn
                Log.w(TAG, "Security WARNING: ${threat.description}")
            }
            SecurityEnforcer.Action.BLOCK -> {
                // PRO tier: block/dialog
                Log.e(TAG, "Security BLOCK: ${threat.description}")
                onCriticalThreat?.invoke(threat)
            }
            SecurityEnforcer.Action.TERMINATE -> {
                // PREMIUM tier: immediate termination
                Log.e(TAG, "Security TERMINATE: ${threat.description}")
                SecurityEnforcer.handle(SecurityEnforcer.Violation.SCREEN_CAPTURE)
            }
        }
    }

    /**
     * Stop all monitoring. Call when call ends.
     */
    fun stopMonitoring(activity: Activity) {
        isMonitoring = false
        periodicCheckRunnable?.let { handler.removeCallbacks(it) }
        periodicCheckRunnable = null

        audioFocusManager.abandonFocus()
        screenRecordingDetector.stopMonitoring(activity)
        microphoneMonitor.stopMonitoring()

        audioFocusManager.onFocusLost = null
        screenRecordingDetector.onRecordingStateChanged = null
        microphoneMonitor.onOtherAppRecording = null
        onSecurityStatusChanged = null
        onCriticalThreat = null

        Log.d(TAG, "All monitoring stopped")
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 5000L
    }
}

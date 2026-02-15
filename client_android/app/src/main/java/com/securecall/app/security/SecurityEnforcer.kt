package com.securecall.app.security

import android.util.Log
import com.securecall.app.config.FeatureFlags

/**
 * Central security policy enforcer.
 *
 * Reacts to security violations according to the current tier's
 * enforcement level (WARN / BLOCK / TERMINATE).
 */
object SecurityEnforcer {

    private const val TAG = "SecurityEnforcer"

    enum class Violation {
        ROOT_DETECTED,
        EMULATOR_DETECTED,
        DEBUGGER_ATTACHED,
        SCREEN_CAPTURE,
        DEVICE_ATTESTATION_FAILED,
        CERTIFICATE_PINNING_FAILED,
        HARDWARE_KEYSTORE_UNAVAILABLE,
        INTEGRITY_CHECK_FAILED
    }

    enum class Action {
        ALLOW,
        WARN,
        BLOCK,
        TERMINATE
    }

    /**
     * Evaluate a security violation and return the required action.
     */
    fun evaluate(violation: Violation): Action {
        // Check if the specific detection is enabled for this tier
        if (!isDetectionEnabled(violation)) {
            return Action.ALLOW
        }

        return when (FeatureFlags.SECURITY_ENFORCEMENT) {
            "TERMINATE" -> Action.TERMINATE
            "BLOCK" -> Action.BLOCK
            else -> Action.WARN
        }
    }

    /**
     * Handle a violation: log it and execute the enforcement action.
     *
     * @return the action that was taken
     */
    fun handle(violation: Violation): Action {
        val action = evaluate(violation)

        when (action) {
            Action.ALLOW -> { /* no-op */ }
            Action.WARN -> {
                Log.w(TAG, "Security warning: $violation [tier=${FeatureFlags.TIER}]")
            }
            Action.BLOCK -> {
                Log.e(TAG, "Security violation BLOCKED: $violation [tier=${FeatureFlags.TIER}]")
            }
            Action.TERMINATE -> {
                Log.e(TAG, "Security violation — TERMINATING: $violation [tier=${FeatureFlags.TIER}]")
                terminateApp()
            }
        }

        return action
    }

    /**
     * Check whether the given violation's detection is enabled
     * in the current flavor's FeatureFlags.
     */
    private fun isDetectionEnabled(violation: Violation): Boolean {
        return when (violation) {
            Violation.ROOT_DETECTED -> FeatureFlags.ROOT_DETECTION_BLOCKS
            Violation.EMULATOR_DETECTED -> FeatureFlags.EMULATOR_DETECTION
            Violation.DEBUGGER_ATTACHED -> FeatureFlags.DEBUGGER_DETECTION
            Violation.SCREEN_CAPTURE -> FeatureFlags.SCREEN_CAPTURE_DETECTION
            Violation.DEVICE_ATTESTATION_FAILED -> FeatureFlags.DEVICE_ATTESTATION_REQUIRED
            Violation.CERTIFICATE_PINNING_FAILED -> FeatureFlags.CERTIFICATE_PINNING
            Violation.HARDWARE_KEYSTORE_UNAVAILABLE -> FeatureFlags.HARDWARE_KEYSTORE_REQUIRED
            Violation.INTEGRITY_CHECK_FAILED -> true // always enabled
        }
    }

    private fun terminateApp() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

package com.securecall.app.security

import android.util.Log
import com.securecall.app.config.FeatureProviderRegistry

/**
 * Central security policy enforcer.
 *
 * Reads feature flags via FeatureProviderRegistry so that runtime
 * tier changes (e.g. FREE→PRO via In-App-Purchase) take effect immediately.
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
        val fp = FeatureProviderRegistry.get()

        if (!isDetectionEnabled(violation)) {
            return Action.ALLOW
        }

        return when (fp.securityEnforcement) {
            "TERMINATE" -> Action.TERMINATE
            "BLOCK" -> Action.BLOCK
            else -> Action.WARN
        }
    }

    /**
     * Handle a violation: log it and execute the enforcement action.
     */
    fun handle(violation: Violation): Action {
        val fp = FeatureProviderRegistry.get()
        val action = evaluate(violation)

        when (action) {
            Action.ALLOW -> { /* no-op */ }
            Action.WARN -> {
                Log.w(TAG, "Security warning: $violation [tier=${fp.tier}]")
            }
            Action.BLOCK -> {
                Log.e(TAG, "Security violation BLOCKED: $violation [tier=${fp.tier}]")
            }
            Action.TERMINATE -> {
                Log.e(TAG, "Security violation — TERMINATING: $violation [tier=${fp.tier}]")
                terminateApp()
            }
        }

        return action
    }

    /**
     * Check whether the given violation's detection is enabled
     * via the current FeatureProvider.
     */
    private fun isDetectionEnabled(violation: Violation): Boolean {
        val fp = FeatureProviderRegistry.get()
        return when (violation) {
            Violation.ROOT_DETECTED -> fp.rootDetectionBlocks
            Violation.EMULATOR_DETECTED -> fp.emulatorDetection
            Violation.DEBUGGER_ATTACHED -> fp.debuggerDetection
            Violation.SCREEN_CAPTURE -> fp.screenCaptureDetection
            Violation.DEVICE_ATTESTATION_FAILED -> fp.deviceAttestationRequired
            Violation.CERTIFICATE_PINNING_FAILED -> fp.certificatePinning
            Violation.HARDWARE_KEYSTORE_UNAVAILABLE -> fp.hardwareKeystoreRequired
            Violation.INTEGRITY_CHECK_FAILED -> true // always enabled
        }
    }

    private fun terminateApp() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

package com.securecall.app.config

/**
 * Feature flags for the PRO tier.
 *
 * Unlimited calls, business-grade security.
 * Blocks on critical security issues, warns on others.
 */
object FeatureFlags {

    const val TIER = "PRO"

    // --- Usage Limits ---
    const val MAX_CALL_DURATION_MINUTES = 0       // unlimited
    const val MAX_CONTACTS = 0                    // unlimited

    // --- Security ---
    const val DEVICE_ATTESTATION_REQUIRED = true
    const val ROOT_DETECTION_BLOCKS = true        // warning, not hard block
    const val CERTIFICATE_PINNING = true
    const val SCREEN_CAPTURE_DETECTION = true     // block recording
    const val DEBUGGER_DETECTION = false
    const val EMULATOR_DETECTION = false
    const val HARDWARE_KEYSTORE_REQUIRED = false
    const val NO_FALLBACK_MODE = false

    // --- Privacy ---
    const val CALL_RECORDING_ALLOWED = false      // enforced
    const val TELEMETRY_ENABLED = false
    const val THIRD_PARTY_ANALYTICS = false

    // --- Network ---
    const val RECONNECT_STRATEGY = "aggressive"
    const val MULTI_DEVICE_SUPPORT = true
    const val AGGRESSIVE_KEY_ROTATION = false

    // --- Logging ---
    const val LOGGING_LEVEL = "WARN"

    // --- Helpers ---
    val isUnlimitedCalls: Boolean get() = MAX_CALL_DURATION_MINUTES == 0
    val isUnlimitedContacts: Boolean get() = MAX_CONTACTS == 0

    /** Security enforcement level: WARN, BLOCK, TERMINATE */
    const val SECURITY_ENFORCEMENT = "BLOCK"
}

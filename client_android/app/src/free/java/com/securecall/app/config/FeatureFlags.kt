package com.securecall.app.config

/**
 * Feature flags for the FREE tier.
 *
 * Basic encrypted calls with usage limits.
 * Minimal security enforcement — warnings only.
 */
object FeatureFlags {

    const val TIER = "FREE"

    // --- Usage Limits ---
    const val MAX_CALL_DURATION_MINUTES = 15
    const val MAX_CONTACTS = 10

    // --- Security ---
    const val DEVICE_ATTESTATION_REQUIRED = false
    const val ROOT_DETECTION_BLOCKS = false       // warn only
    const val CERTIFICATE_PINNING = false
    const val SCREEN_CAPTURE_DETECTION = false
    const val DEBUGGER_DETECTION = false
    const val EMULATOR_DETECTION = false
    const val HARDWARE_KEYSTORE_REQUIRED = false
    const val NO_FALLBACK_MODE = false

    // --- Privacy ---
    const val CALL_RECORDING_ALLOWED = true
    const val TELEMETRY_ENABLED = true            // minimal, opt-out
    const val THIRD_PARTY_ANALYTICS = true

    // --- Network ---
    const val RECONNECT_STRATEGY = "basic"
    const val MULTI_DEVICE_SUPPORT = false
    const val AGGRESSIVE_KEY_ROTATION = false

    // --- Logging ---
    // Fix CLIENT-HIGH-002 (2026-04-16): WARN in release to match build.gradle.
    const val LOGGING_LEVEL = "WARN"

    // --- Helpers ---
    val isUnlimitedCalls: Boolean get() = MAX_CALL_DURATION_MINUTES == 0
    val isUnlimitedContacts: Boolean get() = MAX_CONTACTS == 0

    /** Security enforcement level: WARN, BLOCK, TERMINATE */
    const val SECURITY_ENFORCEMENT = "WARN"
}

package com.securecall.app.config

/**
 * Feature flags for the F-DROID tier (same as FREE but no telemetry).
 */
object FeatureFlags {
    const val TIER = "FREE"
    const val MAX_CALL_DURATION_MINUTES = 15
    const val MAX_CONTACTS = 10
    const val DEVICE_ATTESTATION_REQUIRED = false
    const val ROOT_DETECTION_BLOCKS = false
    const val CERTIFICATE_PINNING = false
    const val SCREEN_CAPTURE_DETECTION = false
    const val DEBUGGER_DETECTION = false
    const val EMULATOR_DETECTION = false
    const val HARDWARE_KEYSTORE_REQUIRED = false
    const val NO_FALLBACK_MODE = false
    const val CALL_RECORDING_ALLOWED = true
    const val TELEMETRY_ENABLED = false  // No telemetry on F-Droid
    const val THIRD_PARTY_ANALYTICS = false
    const val RECONNECT_STRATEGY = "basic"
    const val MULTI_DEVICE_SUPPORT = false
    const val AGGRESSIVE_KEY_ROTATION = false
    // Fix CLIENT-HIGH-002 (2026-04-16): WARN in release — F-Droid flags PII in DEBUG logs.
    const val LOGGING_LEVEL = "WARN"
    val isUnlimitedCalls: Boolean get() = MAX_CALL_DURATION_MINUTES == 0
    val isUnlimitedContacts: Boolean get() = MAX_CONTACTS == 0
    const val SECURITY_ENFORCEMENT = "WARN"
}

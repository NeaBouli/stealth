package com.securecall.app.config

/**
 * Feature flags for the PREMIUM tier.
 *
 * Maximum security. All PRO features plus hardware-backed
 * key storage, anti-tampering, and aggressive key rotation.
 * Terminates app immediately on any security violation.
 */
object FeatureFlags {

    const val TIER = "PREMIUM"

    // --- Usage Limits ---
    const val MAX_CALL_DURATION_MINUTES = 0       // unlimited
    const val MAX_CONTACTS = 0                    // unlimited

    // --- Security ---
    const val DEVICE_ATTESTATION_REQUIRED = true
    const val ROOT_DETECTION_BLOCKS = true        // hard block
    const val CERTIFICATE_PINNING = false // TODO: not yet implemented — enable after CertificatePinner is added
    const val SCREEN_CAPTURE_DETECTION = true     // block
    const val DEBUGGER_DETECTION = true           // terminate app
    const val EMULATOR_DETECTION = true           // hard block
    const val HARDWARE_KEYSTORE_REQUIRED = true
    const val NO_FALLBACK_MODE = true

    // --- Privacy ---
    const val CALL_RECORDING_ALLOWED = false      // enforced
    const val TELEMETRY_ENABLED = false
    const val THIRD_PARTY_ANALYTICS = false

    // --- Network ---
    const val RECONNECT_STRATEGY = "aggressive"
    const val MULTI_DEVICE_SUPPORT = true
    const val AGGRESSIVE_KEY_ROTATION = true

    // --- Logging ---
    const val LOGGING_LEVEL = "ERROR_ONLY"

    // --- Helpers ---
    val isUnlimitedCalls: Boolean get() = MAX_CALL_DURATION_MINUTES == 0
    val isUnlimitedContacts: Boolean get() = MAX_CONTACTS == 0

    /** Security enforcement level: WARN, BLOCK, TERMINATE */
    const val SECURITY_ENFORCEMENT = "TERMINATE"
}

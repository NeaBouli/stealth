package com.securecall.app.config

/**
 * Abstraction layer for feature flags.
 *
 * Direct editions read signed entitlements; the Play edition reads verified
 * subscriptions. Build variants alone never establish a paid entitlement.
 */
interface FeatureProvider {
    val tier: String
    val maxCallDurationMinutes: Int
    val maxContacts: Int
    val deviceAttestationRequired: Boolean
    val rootDetectionBlocks: Boolean
    val certificatePinning: Boolean
    val callRecordingAllowed: Boolean
    val telemetryEnabled: Boolean
    val thirdPartyAnalytics: Boolean
    val reconnectStrategy: String
    val multiDeviceSupport: Boolean
    val screenCaptureDetection: Boolean
    val debuggerDetection: Boolean
    val emulatorDetection: Boolean
    val hardwareKeystoreRequired: Boolean
    val loggingLevel: String
    val noFallbackMode: Boolean
    val aggressiveKeyRotation: Boolean
    val securityEnforcement: String

    val isUnlimitedCalls: Boolean get() = maxCallDurationMinutes == 0
    val isUnlimitedContacts: Boolean get() = maxContacts == 0
}

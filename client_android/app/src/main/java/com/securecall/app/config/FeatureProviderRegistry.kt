package com.securecall.app.config

/**
 * Singleton registry for the active FeatureProvider.
 *
 * Must be initialized via AppInit before any feature check.
 * Before initialization, no paid tier or paid quota is granted.
 */
object FeatureProviderRegistry {

    @Volatile
    private var provider: FeatureProvider? = null

    fun set(fp: FeatureProvider) {
        provider = fp
    }

    fun get(): FeatureProvider {
        return provider ?: DefaultFeatureProvider
    }

    /**
     * Fallback that reads directly from compile-time FeatureFlags.
     * Used before AppInit runs or if set() was never called.
     */
    private object DefaultFeatureProvider : FeatureProvider {
        override val tier: String get() = "FREE"
        override val maxCallDurationMinutes: Int get() = 15
        override val maxContacts: Int get() = 10
        override val deviceAttestationRequired: Boolean get() = FeatureFlags.DEVICE_ATTESTATION_REQUIRED
        override val rootDetectionBlocks: Boolean get() = FeatureFlags.ROOT_DETECTION_BLOCKS
        override val certificatePinning: Boolean get() = FeatureFlags.CERTIFICATE_PINNING
        override val callRecordingAllowed: Boolean get() = FeatureFlags.CALL_RECORDING_ALLOWED
        override val telemetryEnabled: Boolean get() = FeatureFlags.TELEMETRY_ENABLED
        override val thirdPartyAnalytics: Boolean get() = FeatureFlags.THIRD_PARTY_ANALYTICS
        override val reconnectStrategy: String get() = FeatureFlags.RECONNECT_STRATEGY
        override val multiDeviceSupport: Boolean get() = false
        override val screenCaptureDetection: Boolean get() = FeatureFlags.SCREEN_CAPTURE_DETECTION
        override val debuggerDetection: Boolean get() = FeatureFlags.DEBUGGER_DETECTION
        override val emulatorDetection: Boolean get() = FeatureFlags.EMULATOR_DETECTION
        override val hardwareKeystoreRequired: Boolean get() = FeatureFlags.HARDWARE_KEYSTORE_REQUIRED
        override val loggingLevel: String get() = FeatureFlags.LOGGING_LEVEL
        override val noFallbackMode: Boolean get() = FeatureFlags.NO_FALLBACK_MODE
        override val aggressiveKeyRotation: Boolean get() = FeatureFlags.AGGRESSIVE_KEY_ROTATION
        override val securityEnforcement: String get() = FeatureFlags.SECURITY_ENFORCEMENT
    }
}

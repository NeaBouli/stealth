package com.securecall.app.config

import android.content.Context
import com.securecall.app.billing.SubscriptionManager
import com.securecall.app.billing.SubscriptionTier

/**
 * FREE-flavor FeatureProvider that reads the active subscription tier
 * at runtime from SubscriptionManager.
 *
 * When the user upgrades via In-App-Purchase, features unlock immediately.
 */
class RuntimeFeatureProvider(context: Context) : FeatureProvider {

    private val subManager = SubscriptionManager(context)

    private val currentTier: SubscriptionTier
        get() = subManager.getCurrentTier()

    override val tier: String
        get() = currentTier.name

    override val maxCallDurationMinutes: Int
        get() = when (currentTier) {
            SubscriptionTier.FREE -> 15
            else -> 0
        }

    override val maxContacts: Int
        get() = when (currentTier) {
            SubscriptionTier.FREE -> 10
            else -> 0
        }

    override val deviceAttestationRequired: Boolean
        get() = currentTier != SubscriptionTier.FREE

    override val rootDetectionBlocks: Boolean
        get() = currentTier != SubscriptionTier.FREE

    override val certificatePinning: Boolean
        get() = currentTier != SubscriptionTier.FREE

    override val callRecordingAllowed: Boolean
        get() = currentTier == SubscriptionTier.FREE

    override val telemetryEnabled: Boolean
        get() = currentTier == SubscriptionTier.FREE

    override val thirdPartyAnalytics: Boolean
        get() = currentTier == SubscriptionTier.FREE

    override val reconnectStrategy: String
        get() = when (currentTier) {
            SubscriptionTier.FREE -> "basic"
            else -> "aggressive"
        }

    override val multiDeviceSupport: Boolean
        get() = currentTier != SubscriptionTier.FREE

    override val screenCaptureDetection: Boolean
        get() = currentTier == SubscriptionTier.PREMIUM

    override val debuggerDetection: Boolean
        get() = currentTier == SubscriptionTier.PREMIUM

    override val emulatorDetection: Boolean
        get() = currentTier == SubscriptionTier.PREMIUM

    override val hardwareKeystoreRequired: Boolean
        get() = currentTier == SubscriptionTier.PREMIUM

    override val loggingLevel: String
        get() = when (currentTier) {
            SubscriptionTier.FREE -> "DEBUG"
            SubscriptionTier.PRO -> "WARN"
            SubscriptionTier.PREMIUM -> "ERROR_ONLY"
        }

    override val noFallbackMode: Boolean
        get() = currentTier == SubscriptionTier.PREMIUM

    override val aggressiveKeyRotation: Boolean
        get() = currentTier == SubscriptionTier.PREMIUM

    override val securityEnforcement: String
        get() = when (currentTier) {
            SubscriptionTier.FREE -> "WARN"
            SubscriptionTier.PRO -> "BLOCK"
            SubscriptionTier.PREMIUM -> "TERMINATE"
        }
}

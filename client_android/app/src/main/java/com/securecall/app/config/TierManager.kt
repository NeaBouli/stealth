package com.securecall.app.config

import android.content.Context
import android.util.Log
import com.securecall.app.billing.SubscriptionManager
import com.securecall.app.billing.DirectEntitlementStore

/**
 * Manages the effective tier for the app.
 * Direct builds require a signed license. Play builds use their server-verified
 * subscription. Build flavors and legacy activation strings are not proof.
 */
object TierManager {
    private const val TAG = "TierManager"
    private const val PREFS = "securecall_prefs"
    private const val KEY_ACTIVATED_TIER = "activated_tier"

    private val TIER_RANK = mapOf("free" to 0, "pro" to 1, "premium" to 2)

    fun getCurrentTier(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_ACTIVATED_TIER)) {
            prefs.edit().remove(KEY_ACTIVATED_TIER).commit()
        }
        val verifiedTier = try {
            if (com.securecall.app.BuildConfig.FLAVOR != "free") {
                return DirectEntitlementStore(context.applicationContext).currentTier()
            }
            SubscriptionManager(context.applicationContext).getCurrentTier().name.lowercase()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read subscription tier: ${t.message}")
            ""
        }

        val effective = listOf(verifiedTier)
            .filter { it in TIER_RANK }
            .maxByOrNull { TIER_RANK[it] ?: 0 }
            ?: "free"
        return effective.uppercase()
    }

    fun isFreeTier(context: Context): Boolean = getCurrentTier(context) == "FREE"
    fun isProOrHigher(context: Context): Boolean = getCurrentTier(context) in listOf("PRO", "PREMIUM")
    fun isPremium(context: Context): Boolean = getCurrentTier(context) == "PREMIUM"

    /**
     * Apply the current effective tier to the FeatureProviderRegistry.
     * Call this on app startup and after activation code success.
     */
    fun applyTier(context: Context) {
        val tier = getCurrentTier(context)
        Log.d(TAG, "Applying tier: $tier (build=${com.securecall.app.BuildConfig.FLAVOR}, activated=${
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVATED_TIER, "none")
        })")
        // Always replace the provider so a revoked or expired entitlement takes
        // effect immediately instead of surviving in process memory.
        FeatureProviderRegistry.set(EffectiveTierFeatureProvider(context.applicationContext))
    }

    /**
     * FeatureProvider for the current build or verified runtime tier.
     */
    private class EffectiveTierFeatureProvider(private val context: Context) : FeatureProvider {
        private val isPro get() = tier == "PRO" || tier == "PREMIUM"
        private val isPremium get() = tier == "PREMIUM"

        override val tier: String get() = getCurrentTier(context)
        override val maxCallDurationMinutes: Int get() = if (isPro) 0 else 15
        override val maxContacts: Int get() = if (isPro) 0 else 10
        override val deviceAttestationRequired: Boolean get() = isPro
        override val rootDetectionBlocks: Boolean get() = isPro
        override val certificatePinning: Boolean get() = true
        override val callRecordingAllowed: Boolean get() = !isPro
        override val telemetryEnabled: Boolean get() = !isPro && com.securecall.app.BuildConfig.FLAVOR == "free"
        override val thirdPartyAnalytics: Boolean get() = telemetryEnabled
        override val reconnectStrategy: String get() = if (isPro) "aggressive" else "basic"
        override val multiDeviceSupport: Boolean get() = isPro
        override val screenCaptureDetection: Boolean get() = isPro
        override val debuggerDetection: Boolean get() = isPremium
        override val emulatorDetection: Boolean get() = isPremium
        override val hardwareKeystoreRequired: Boolean get() = isPremium
        override val loggingLevel: String get() = when {
            isPremium -> "ERROR_ONLY"
            isPro -> "WARN"
            else -> "DEBUG"
        }
        override val noFallbackMode: Boolean get() = isPremium
        override val aggressiveKeyRotation: Boolean get() = isPremium
        override val securityEnforcement: String get() = when {
            isPremium -> "TERMINATE"
            isPro -> "BLOCK"
            else -> "WARN"
        }
    }
}

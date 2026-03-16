package com.securecall.app.config

import android.content.Context
import android.util.Log

/**
 * Manages the effective tier for the app.
 * Checks activated_tier from SharedPreferences (activation code unlock),
 * then falls back to BuildConfig.FLAVOR (compile-time tier).
 * Returns the highest tier available.
 */
object TierManager {
    private const val TAG = "TierManager"
    private const val PREFS = "securecall_prefs"
    private const val KEY_ACTIVATED_TIER = "activated_tier"

    private val TIER_RANK = mapOf("free" to 0, "pro" to 1, "premium" to 2)

    fun getCurrentTier(context: Context): String {
        val buildTier = com.securecall.app.BuildConfig.FLAVOR.lowercase()
        val activatedTier = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVATED_TIER, null)?.lowercase() ?: ""

        val buildRank = TIER_RANK[buildTier] ?: 0
        val activatedRank = TIER_RANK[activatedTier] ?: 0

        val effective = if (activatedRank > buildRank) activatedTier else buildTier
        return effective.uppercase()
    }

    fun isFreeTier(context: Context): Boolean = getCurrentTier(context) == "FREE"
    fun isProOrHigher(context: Context): Boolean = getCurrentTier(context) in listOf("PRO", "PREMIUM")
    fun isPremium(context: Context): Boolean = getCurrentTier(context) == "PREMIUM"

    fun setActivatedTier(context: Context, tier: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVATED_TIER, tier.lowercase()).apply()
        Log.d(TAG, "Activated tier set to: $tier")
        // Update FeatureProviderRegistry with new tier's features
        applyTier(context)
    }

    /**
     * Apply the current effective tier to the FeatureProviderRegistry.
     * Call this on app startup and after activation code success.
     */
    fun applyTier(context: Context) {
        val tier = getCurrentTier(context)
        Log.d(TAG, "Applying tier: $tier (build=${com.securecall.app.BuildConfig.FLAVOR}, activated=${
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVATED_TIER, "none")
        })")
        if (tier != com.securecall.app.BuildConfig.FLAVOR.uppercase()) {
            // Activated tier is higher than build tier — override the registry
            FeatureProviderRegistry.set(ActivatedFeatureProvider(tier))
        }
    }

    /**
     * FeatureProvider that returns features for an activated tier.
     * Used when activation code unlocks a higher tier than the build flavor.
     */
    private class ActivatedFeatureProvider(private val activatedTier: String) : FeatureProvider {
        private val isPro get() = activatedTier == "PRO" || activatedTier == "PREMIUM"
        private val isPremium get() = activatedTier == "PREMIUM"

        override val tier: String get() = activatedTier
        override val maxCallDurationMinutes: Int get() = if (isPro) 0 else 15
        override val maxContacts: Int get() = if (isPro) 0 else 10
        override val deviceAttestationRequired: Boolean get() = isPro
        override val rootDetectionBlocks: Boolean get() = isPro
        override val certificatePinning: Boolean get() = isPro
        override val callRecordingAllowed: Boolean get() = !isPro
        override val telemetryEnabled: Boolean get() = !isPro
        override val thirdPartyAnalytics: Boolean get() = !isPro
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

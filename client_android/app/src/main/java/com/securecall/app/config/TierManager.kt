package com.securecall.app.config

import android.content.Context
import android.util.Log
import com.securecall.app.billing.SubscriptionManager

/**
 * Manages the effective tier for the app.
 * Checks build flavor and server-verified subscription state. Legacy activation
 * state is ignored and removed when activation-code support is disabled.
 * Returns the highest tier available.
 */
object TierManager {
    private const val TAG = "TierManager"
    private const val PREFS = "securecall_prefs"
    private const val KEY_ACTIVATED_TIER = "activated_tier"

    private val TIER_RANK = mapOf("free" to 0, "pro" to 1, "premium" to 2)

    fun getCurrentTier(context: Context): String {
        val buildTier = com.securecall.app.BuildConfig.FLAVOR.lowercase()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activatedTier = if (com.securecall.app.BuildConfig.ACTIVATION_CODE_ENABLED) {
            prefs.getString(KEY_ACTIVATED_TIER, null)?.lowercase() ?: ""
        } else {
            if (prefs.contains(KEY_ACTIVATED_TIER)) {
                prefs.edit().remove(KEY_ACTIVATED_TIER).commit()
            }
            ""
        }
        val subscriptionTier = try {
            SubscriptionManager(context.applicationContext).getCurrentTier().name.lowercase()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read subscription tier: ${t.message}")
            ""
        }

        val effective = listOf(buildTier, subscriptionTier, activatedTier)
            .filter { it in TIER_RANK }
            .maxByOrNull { TIER_RANK[it] ?: 0 }
            ?: "free"
        return effective.uppercase()
    }

    fun isFreeTier(context: Context): Boolean = getCurrentTier(context) == "FREE"
    fun isProOrHigher(context: Context): Boolean = getCurrentTier(context) in listOf("PRO", "PREMIUM")
    fun isPremium(context: Context): Boolean = getCurrentTier(context) == "PREMIUM"

    fun setActivatedTier(context: Context, tier: String) {
        if (!com.securecall.app.BuildConfig.ACTIVATION_CODE_ENABLED) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_ACTIVATED_TIER).commit()
            Log.w(TAG, "Ignoring activation tier because activation codes are disabled")
            return
        }
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
        // Always replace the provider so a revoked or expired entitlement takes
        // effect immediately instead of surviving in process memory.
        FeatureProviderRegistry.set(EffectiveTierFeatureProvider(tier))
    }

    /**
     * FeatureProvider for the current build or verified runtime tier.
     */
    private class EffectiveTierFeatureProvider(private val activatedTier: String) : FeatureProvider {
        private val isPro get() = activatedTier == "PRO" || activatedTier == "PREMIUM"
        private val isPremium get() = activatedTier == "PREMIUM"

        override val tier: String get() = activatedTier
        override val maxCallDurationMinutes: Int get() = if (isPro) 0 else FeatureFlags.MAX_CALL_DURATION_MINUTES
        override val maxContacts: Int get() = if (isPro) 0 else FeatureFlags.MAX_CONTACTS
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

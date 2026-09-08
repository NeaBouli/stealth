package com.securecall.app.init

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.securecall.app.config.FeatureProviderRegistry

/**
 * PREMIUM-flavor initialization.
 * Applies verified access and configures Crashlytics.
 */
object AppInit {
    private const val TAG = "AppInit"

    fun init(context: Context) {
        com.securecall.app.config.TierManager.applyTier(context)

        // Phase 8: Crashlytics — disabled for PREMIUM (TELEMETRY_ENABLED=false)
        // Keep startup resilient if Firebase initialization is temporarily unavailable.
        try {
            FirebaseCrashlytics.getInstance()
                .setCrashlyticsCollectionEnabled(FeatureProviderRegistry.get().telemetryEnabled)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not initialized, skipping Crashlytics setup", e)
        }
    }
}

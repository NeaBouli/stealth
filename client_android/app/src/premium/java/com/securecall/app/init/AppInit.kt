package com.securecall.app.init

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.securecall.app.config.CompileTimeFeatureProvider
import com.securecall.app.config.FeatureProviderRegistry

/**
 * PREMIUM-flavor initialization.
 * Sets up CompileTimeFeatureProvider, configures Crashlytics.
 */
object AppInit {
    private const val TAG = "AppInit"

    fun init(context: Context) {
        FeatureProviderRegistry.set(CompileTimeFeatureProvider())

        // Phase 8: Crashlytics — disabled for PREMIUM (TELEMETRY_ENABLED=false)
        // Guard: Firebase may not be initialized if using placeholder credentials
        try {
            FirebaseCrashlytics.getInstance()
                .setCrashlyticsCollectionEnabled(FeatureProviderRegistry.get().telemetryEnabled)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not initialized, skipping Crashlytics setup", e)
        }
    }
}

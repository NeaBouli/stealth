package com.securecall.app.init

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.securecall.app.config.CompileTimeFeatureProvider
import com.securecall.app.config.FeatureProviderRegistry

/**
 * PREMIUM-flavor initialization.
 * Sets up CompileTimeFeatureProvider, configures Crashlytics.
 */
object AppInit {
    fun init(context: Context) {
        FeatureProviderRegistry.set(CompileTimeFeatureProvider())

        // Phase 8: Crashlytics — disabled for PREMIUM (TELEMETRY_ENABLED=false)
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(FeatureProviderRegistry.get().telemetryEnabled)
    }
}

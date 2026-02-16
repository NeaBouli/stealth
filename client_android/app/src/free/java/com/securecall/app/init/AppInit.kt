package com.securecall.app.init

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.securecall.app.billing.LicenseChecker
import com.securecall.app.config.FeatureProviderRegistry
import com.securecall.app.config.RuntimeFeatureProvider

/**
 * FREE-flavor initialization.
 * Sets up RuntimeFeatureProvider, runs license check, configures Crashlytics.
 */
object AppInit {
    fun init(context: Context) {
        FeatureProviderRegistry.set(RuntimeFeatureProvider(context))
        LicenseChecker.checkAtStartup(context)

        // Phase 8: Crashlytics — enabled for FREE (TELEMETRY_ENABLED=true)
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(FeatureProviderRegistry.get().telemetryEnabled)
    }
}

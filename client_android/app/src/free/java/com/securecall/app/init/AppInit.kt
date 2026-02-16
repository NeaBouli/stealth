package com.securecall.app.init

import android.content.Context
import com.securecall.app.billing.LicenseChecker
import com.securecall.app.config.FeatureProviderRegistry
import com.securecall.app.config.RuntimeFeatureProvider

/**
 * FREE-flavor initialization.
 * Sets up RuntimeFeatureProvider and runs license check.
 */
object AppInit {
    fun init(context: Context) {
        FeatureProviderRegistry.set(RuntimeFeatureProvider(context))
        LicenseChecker.checkAtStartup(context)
    }
}

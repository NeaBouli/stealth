package com.securecall.app.init

import android.content.Context
import com.securecall.app.config.CompileTimeFeatureProvider
import com.securecall.app.config.FeatureProviderRegistry

/**
 * PREMIUM-flavor initialization.
 * Sets up CompileTimeFeatureProvider (delegates to FeatureFlags).
 */
object AppInit {
    fun init(context: Context) {
        FeatureProviderRegistry.set(CompileTimeFeatureProvider())
    }
}

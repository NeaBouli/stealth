package com.securecall.app.init

import android.content.Context
import android.util.Log
import com.securecall.app.config.CompileTimeFeatureProvider
import com.securecall.app.config.FeatureProviderRegistry

/** F-Droid flavor initialization — no Firebase, no billing, no ads. */
object AppInit {
    private const val TAG = "AppInit"

    fun init(context: Context) {
        FeatureProviderRegistry.set(CompileTimeFeatureProvider())
        Log.d(TAG, "F-Droid flavor initialized — no Google services")
    }
}

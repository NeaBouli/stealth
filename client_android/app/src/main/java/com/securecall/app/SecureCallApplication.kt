package com.securecall.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class SecureCallApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

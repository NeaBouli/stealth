package com.securecall.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class SecureCallApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Initialize WebRTC PeerConnectionFactory early
        org.webrtc.PeerConnectionFactory.initialize(
            org.webrtc.PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )
    }
}

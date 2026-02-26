package com.securecall.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

class SecureCallApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Restore dark mode preference before any activity renders
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        when (prefs.getString("pref_dark_mode", "system")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        // Initialize WebRTC PeerConnectionFactory early
        org.webrtc.PeerConnectionFactory.initialize(
            org.webrtc.PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )
    }
}

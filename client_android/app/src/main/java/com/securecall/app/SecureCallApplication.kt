package com.securecall.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

class SecureCallApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // CRITICAL: Start the foreground service FIRST — before any other work.
        // On Android 8 (Galaxy S7), the 5-second startForeground() timeout starts
        // from this call. The service's onCreate() runs as soon as Application.onCreate()
        // returns (before Activity.onCreate()), giving it the earliest chance to call
        // startForeground(). If we delay this to MainActivity.onCreate(), the service
        // has to wait for the entire Activity setup to complete first.
        try {
            val wsIntent = android.content.Intent(this, com.securecall.app.net.WebSocketService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(this, wsIntent)
        } catch (e: Exception) {
            android.util.Log.e("SecureCallApp", "Failed to start foreground service", e)
        }

        // Restore dark mode preference before any activity renders (default: dark)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        when (prefs.getString("pref_dark_mode", "dark")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // dark = default
        }

        // Initialize WebRTC PeerConnectionFactory on a background thread.
        // This loads the native libjingle_peerconnection_so.so library which can
        // take 100-500ms. Running on the main thread delays service startForeground().
        Thread({
            org.webrtc.PeerConnectionFactory.initialize(
                org.webrtc.PeerConnectionFactory.InitializationOptions.builder(this)
                    .createInitializationOptions()
            )
            android.util.Log.d("SecureCallApp", "WebRTC PeerConnectionFactory initialized")
        }, "webrtc-init").start()

        // Initialize WalletConnect / Reown AppKit (non-blocking)
        // Catch Throwable (not just Exception) to handle NoClassDefFoundError / LinkageError
        // that can occur during class loading if WalletConnect's android-core pulls in
        // Firebase/PushClient refs that aren't bundled. See F-010 / Crashlytics issue
        // in 1.0.13-free (vC34) where ClassNotFoundException crashed 2 users.
        try {
            com.securecall.app.wallet.WalletConnectManager.init(this)
        } catch (e: Throwable) {
            android.util.Log.e("SecureCallApp", "WalletConnect init failed (non-fatal)", e)
        }
    }
}

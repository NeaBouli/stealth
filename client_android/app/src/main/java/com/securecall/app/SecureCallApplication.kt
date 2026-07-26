package com.securecall.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.securecall.app.net.ForegroundServicePolicy
import com.securecall.app.net.WebSocketService

class SecureCallApplication : Application() {
    private val backgroundStopHandler = Handler(Looper.getMainLooper())
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                backgroundStopHandler.removeCallbacksAndMessages(null)
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) {
                    backgroundStopHandler.postDelayed({
                        // Android 15+ (API 35): persistent idle signaling is not allowed —
                        // stop the service after backgrounding even if the legacy preference
                        // is still true. An active call always keeps the service alive.
                        val persistentIdleAllowed = ForegroundServicePolicy.allowsPersistentIdleSignaling(
                            android.os.Build.VERSION.SDK_INT
                        )
                        if (startedActivityCount == 0 &&
                            (!persistentIdleAllowed ||
                                !WebSocketService.isBackgroundServiceEnabled(this@SecureCallApplication)) &&
                            !WebSocketService.hasActiveCall()
                        ) {
                            android.util.Log.d("SecureCallApp", "Background service disabled or not allowed on this API; stopping WebSocketService after app background")
                            stopService(android.content.Intent(this@SecureCallApplication, WebSocketService::class.java))
                        }
                    }, 15_000L)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        // CRITICAL: Start the foreground service first when always-on mode is enabled.
        // On Android 8 (Galaxy S7), the 5-second startForeground() timeout starts
        // from this call. The service's onCreate() runs as soon as Application.onCreate()
        // returns, giving it the earliest chance to call startForeground().
        // Android 15+ (API 35): never auto-start the persistent service on process
        // launch — incoming calls are delivered via FCM secure push notifications.
        if (ForegroundServicePolicy.allowsPersistentIdleSignaling(android.os.Build.VERSION.SDK_INT) &&
            WebSocketService.isBackgroundServiceEnabled(this)
        ) {
            try {
                val wsIntent = android.content.Intent(this, WebSocketService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(this, wsIntent)
            } catch (e: Exception) {
                android.util.Log.e("SecureCallApp", "Failed to start foreground service", e)
            }
        } else {
            android.util.Log.d("SecureCallApp", "Background service disabled; skipping startup WebSocketService")
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

    }
}

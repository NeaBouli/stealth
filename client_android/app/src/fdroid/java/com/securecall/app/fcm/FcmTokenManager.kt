package com.securecall.app.fcm

import android.content.Context

/**
 * F-Droid stub: no Firebase, no FCM token registration.
 * Push notifications use WebSocket only in the F-Droid edition.
 */
object FcmTokenManager {

    fun ensureTokenRegistered(context: Context) {
        // No-op: F-Droid build has no Firebase
    }

    fun onTokenRefreshed(context: Context, token: String) {
        // No-op
    }

    fun getStoredToken(context: Context): String? = null
}

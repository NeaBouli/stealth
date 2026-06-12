package com.securecall.app.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.securecall.app.net.WebSocketService

/**
 * FIX 9 — FCM Push Notifications placeholder.
 *
 * TODO: FCM requires a real Firebase project with google-services.json.
 * Current state: Firebase is disabled in AndroidManifest (placeholder credentials).
 * FCM token registration will silently fail (caught in try/catch).
 *
 * Integration plan:
 * 1. Create Firebase project at console.firebase.google.com
 * 2. Add Android apps for all 3 package names (free, pro, premium)
 * 3. Download google-services.json to client_android/app/
 * 4. Enable FirebaseInitProvider in AndroidManifest.xml
 * 5. Server already handles REGISTER_FCM_TOKEN and sends push via fcm.js
 * 6. Test: kill app on S7, call from S10, verify push wakes S7
 */
object FcmTokenManager {

    private const val TAG = "FCM_TOKEN"
    private const val PREFS_NAME = "securecall_fcm"
    private const val KEY_TOKEN = "fcm_token"

    fun ensureTokenRegistered(context: Context) {
        sendStoredTokenToBackend(context)
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "FCM token obtained")
                    saveToken(context, token)
                    sendTokenToBackend(token)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get FCM token", e)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not initialized, skipping FCM token registration", e)
        }
    }

    fun onTokenRefreshed(context: Context, token: String) {
        Log.d(TAG, "Token refreshed, updating")
        saveToken(context, token)
        sendTokenToBackend(token)
    }

    fun getStoredToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    fun sendStoredTokenToBackend(context: Context) {
        getStoredToken(context)?.let { sendTokenToBackend(it) }
    }

    private fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    private fun sendTokenToBackend(token: String) {
        val ws = WebSocketService.instance
        if (ws != null && ws.isRegistered) {
            val json = org.json.JSONObject()
                .put("type", "REGISTER_FCM_TOKEN")
                .put("fcmToken", token)
                .toString()
            ws.sendMessage(json)
            Log.d(TAG, "REGISTER_FCM_TOKEN sent to backend")
        } else {
            Log.w(TAG, "Not yet registered, FCM token will be sent after REGISTER")
        }
    }
}

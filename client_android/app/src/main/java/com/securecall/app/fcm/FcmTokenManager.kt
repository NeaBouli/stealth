package com.securecall.app.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.securecall.app.net.WebSocketService

object FcmTokenManager {

    private const val TAG = "FCM_TOKEN"
    private const val PREFS_NAME = "securecall_fcm"
    private const val KEY_TOKEN = "fcm_token"

    fun ensureTokenRegistered(context: Context) {
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

    private fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    private fun sendTokenToBackend(token: String) {
        val ws = WebSocketService.instance
        if (ws != null) {
            val json = """
                {
                  "type": "REGISTER_FCM_TOKEN",
                  "fcmToken": "$token"
                }
            """.trimIndent()
            ws.sendMessage(json)
            Log.d(TAG, "REGISTER_FCM_TOKEN sent to backend")
        } else {
            Log.w(TAG, "WebSocketService not available, token will be sent on next connect")
        }
    }
}

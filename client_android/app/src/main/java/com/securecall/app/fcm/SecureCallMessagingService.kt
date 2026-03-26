package com.securecall.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.securecall.app.CallActivity
import com.securecall.app.R

class SecureCallMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_SERVICE"
        private const val CHANNEL_ID = "securecall_incoming_call"
        private const val NOTIFICATION_ID = 9001
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        FcmTokenManager.onTokenRefreshed(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM message received: ${message.data}")

        val type = message.data["type"] ?: return
        when (type) {
            "CALL_INVITE" -> {
                val sessionId = message.data["sessionId"] ?: ""
                val callerName = message.data["callerName"] ?: "Unknown"
                Log.d(TAG, "Incoming call push: session=$sessionId, caller=$callerName")
                showIncomingCallNotification(sessionId, callerName)
            }
            "EMERGENCY_BROADCAST" -> {
                val templateId = message.data["template_id"]?.toIntOrNull() ?: -1
                Log.d(TAG, "Emergency broadcast push: template_id=$templateId")
                com.securecall.app.emergency.EmergencyBroadcastManager.handleBroadcast(this, templateId)
            }
            else -> {
                Log.w(TAG, "Unknown FCM message type: $type")
            }
        }
    }

    private fun showIncomingCallNotification(sessionId: String, callerName: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming secure calls"
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, com.securecall.app.IncomingCallActivity::class.java).apply {
            putExtra("fromNotification", true)
            putExtra("sessionId", sessionId)
            putExtra("callerName", callerName)
            putExtra("isIncoming", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming Secure Call")
            .setContentText("Call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}

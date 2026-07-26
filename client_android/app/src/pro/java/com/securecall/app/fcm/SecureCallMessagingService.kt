package com.securecall.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.securecall.app.R
import com.securecall.app.notifications.IncomingCallNotifications

class SecureCallMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_SERVICE"
        private const val CHANNEL_ID = "securecall_incoming_call_urgent"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        FcmTokenManager.onTokenRefreshed(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM message received: ${message.data}")
        com.securecall.app.debug.SecLogManager.logIfEnabled(this, "FCM", "Received: ${message.data["type"] ?: "unknown"}")

        val type = message.data["type"] ?: return
        when (type) {
            "CALL_INVITE" -> {
                val sessionId = message.data["sessionId"] ?: ""
                val callerName = message.data["callerName"] ?: "Unknown"
                val callerClientId = message.data["callerClientId"] ?: ""
                val callerPhone = message.data["callerPhone"] ?: ""
                Log.d(TAG, "Incoming call push: session=$sessionId, caller=$callerName, clientId=$callerClientId")
                com.securecall.app.debug.SecLogManager.log("FCM", "CALL_INVITE: session=$sessionId, caller=$callerName")
                handleFcmCallInvite(sessionId, callerName, callerClientId, callerPhone)
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

    /**
     * BUG-010: FCM CALL_INVITE — start IncomingCallActivity DIRECTLY without waiting for WS.
     * All call data comes from FCM payload. WS reconnects in background for CALL_ACCEPT.
     */
    private fun handleFcmCallInvite(sessionId: String, callerName: String, callerClientId: String, callerPhone: String) {
        // A) Acquire WakeLock immediately to keep CPU alive for ringing
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SecureCall:FCMCallWakeup"
        )
        wl.acquire(60_000L) // 60s max — will be released when call is answered/declined
        Log.d(TAG, "WakeLock acquired for FCM call wakeup")

        // B) Wake signaling first. The explicit action lets a newly-created service
        //    register the FCM session and own ringtone/vibration before the activity
        //    is shown; this also works when no persistent service exists on API 35+.
        val wsIntent = Intent(this, com.securecall.app.net.WebSocketService::class.java).apply {
            action = com.securecall.app.net.WebSocketService.ACTION_FCM_CALL_INVITE
            putExtra(com.securecall.app.net.WebSocketService.EXTRA_FCM_SESSION_ID, sessionId)
        }
        val signalingStartRequested = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(wsIntent)
            } else {
                startService(wsIntent)
            }
            Log.d(TAG, "WebSocketService wake requested for CALL_ACCEPT delivery")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WebSocketService from FCM", e)
            false
        }

        // D) Start IncomingCallActivity DIRECTLY — no WS needed for ringing
        val intent = Intent(this, com.securecall.app.IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("sessionId", sessionId)
            putExtra("callerClientId", callerClientId)
            putExtra("callerPhone", callerPhone)
            putExtra("callerName", callerName)
            putExtra("from_fcm", true) // Flag: came from FCM, WS may not be connected
        }
        try {
            startActivity(intent)
            Log.d(TAG, "IncomingCallActivity launched directly from FCM for $callerName")
            com.securecall.app.debug.SecLogManager.log("FCM", "IncomingCallActivity launched directly")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch IncomingCallActivity from FCM", e)
            com.securecall.app.debug.SecLogManager.log("FCM", "FAILED to launch IncomingCallActivity: ${e.message}")
        }

        // D) Show full-screen notification as backup (lock screen / DND / Android 10+)
        showIncomingCallNotification(
            sessionId, callerName, callerClientId, callerPhone,
            serviceHandlesRingtone = signalingStartRequested
        )

        // E) Force reconnect if an existing service instance is disconnected.
        com.securecall.app.net.WebSocketService.instance?.let { ws ->
            if (!ws.isConnected) {
                ws.forceReconnect()
                Log.d(TAG, "Forced WS reconnect for FCM call")
            }
        }

        // Release WakeLock after activity launch (activity manages its own screen wake)
        try {
            if (wl.isHeld) wl.release()
        } catch (_: Exception) {}
    }

    private fun showIncomingCallNotification(
        sessionId: String,
        callerName: String,
        callerClientId: String,
        callerPhone: String,
        serviceHandlesRingtone: Boolean
    ) {
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
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, com.securecall.app.IncomingCallActivity::class.java).apply {
            putExtra("fromNotification", true)
            putExtra("sessionId", sessionId)
            putExtra("callerName", callerName)
            putExtra("callerClientId", callerClientId)
            putExtra("callerPhone", callerPhone)
            putExtra("isIncoming", true)
            putExtra("from_fcm", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Incoming Secure Call")
            .setContentText("Call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(serviceHandlesRingtone)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        if (!serviceHandlesRingtone) {
            // If Android rejects the FGS start, keep the channel sound repeating
            // until the user opens or dismisses the incoming-call notification.
            notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        }
        manager.notify(IncomingCallNotifications.FCM_BACKUP_NOTIFICATION_ID, notification)
    }
}

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
import com.securecall.app.security.IdentityProtocol
import com.securecall.app.security.IdentitySigningKey
import com.securecall.app.security.VerifiedCallInvite
import org.json.JSONObject

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
            "CALL_INVITE_V2" -> {
                val verified = verifyCallInvite(message.data)
                if (verified == null) {
                    Log.w(TAG, "Rejected invalid authenticated call push")
                    return
                }
                val (invite, payload) = verified
                val callerName = com.securecall.app.data.PhoneBookResolver.resolveCallerName(
                    this, invite.fromClientId, invite.callerPhone
                )
                handleFcmCallInvite(invite, payload, callerName)
            }
            "IDENTITY_MIGRATION_CHALLENGE" -> {
                handleIdentityMigrationChallenge(message.data)
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

    /** Accept only a signed v2 invite before any wake-up UI or ringtone is started. */
    private fun verifyCallInvite(data: Map<String, String>): Pair<VerifiedCallInvite, String>? {
        val invite = VerifiedCallInvite(
            sessionId = data["sessionId"].orEmpty(),
            fromClientId = data["from"].orEmpty(),
            fromIdentityId = data["fromIdentityId"].orEmpty(),
            to = data["to"].orEmpty(),
            requestedTo = data["requestedTo"].orEmpty(),
            ephemeralPublicKey = data["ephemeralPublicKey"].orEmpty(),
            identityPublicKey = data["identityPublicKey"].orEmpty(),
            issuedAt = data["issuedAt"]?.toLongOrNull() ?: return null,
            nonce = data["nonce"].orEmpty(),
            signature = data["signature"].orEmpty(),
            inviteDigest = data["inviteDigest"].orEmpty(),
            callerPhone = data["callerPhone"].orEmpty().takeIf { it.length <= 64 } ?: return null,
        )
        val identity = IdentitySigningKey.existingIdentity() ?: return null
        val localId = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
            .getString("client_id", null)
        if (invite.to != identity.identityId || localId != identity.identityId
            || !IdentityProtocol.verifyCallInvite(invite, System.currentTimeMillis() / 1000)) return null
        val payload = JSONObject().apply {
            put("type", "CALL_INVITE_V2")
            put("sessionId", invite.sessionId)
            put("from", invite.fromClientId)
            put("fromIdentityId", invite.fromIdentityId)
            put("to", invite.to)
            put("requestedTo", invite.requestedTo)
            put("ephemeralPublicKey", invite.ephemeralPublicKey)
            put("identityPublicKey", invite.identityPublicKey)
            put("issuedAt", invite.issuedAt)
            put("nonce", invite.nonce)
            put("signature", invite.signature)
            put("inviteDigest", invite.inviteDigest)
            put("callerPhone", invite.callerPhone)
        }.toString()
        return invite to payload
    }

    private fun handleIdentityMigrationChallenge(data: Map<String, String>) {
        val identity = IdentitySigningKey.existingIdentity() ?: return
        val requested = data["requestedClientId"].orEmpty()
        val challengeId = data["challengeId"].orEmpty()
        val challenge = data["challenge"].orEmpty()
        val expiresAt = data["expiresAt"]?.toLongOrNull() ?: return
        if (data["identityId"] != identity.identityId
            || !IdentityProtocol.validateRegistrationChallenge(
                challengeId, challenge, requested, identity, expiresAt,
                System.currentTimeMillis() / 1000,
            )) return
        val payload = JSONObject().apply {
            put("type", "IDENTITY_MIGRATION_CHALLENGE")
            put("challengeId", challengeId)
            put("challenge", challenge)
            put("requestedClientId", requested)
            put("identityId", identity.identityId)
            put("expiresAt", expiresAt)
        }.toString()
        val intent = Intent(this, com.securecall.app.net.WebSocketService::class.java).apply {
            action = com.securecall.app.net.WebSocketService.ACTION_IDENTITY_MIGRATION_CHALLENGE
            putExtra(com.securecall.app.net.WebSocketService.EXTRA_IDENTITY_CHALLENGE, payload)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deliver identity migration challenge", e)
        }
    }

    private fun handleFcmCallInvite(invite: VerifiedCallInvite, payload: String, callerName: String) {
        val existingService = com.securecall.app.net.WebSocketService.instance
        if (existingService != null && !existingService.acceptFcmInvite(payload)) {
            Log.w(TAG, "WebSocket service rejected authenticated call push")
            return
        }
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
            action = com.securecall.app.net.WebSocketService.ACTION_FCM_CALL_INVITE_V2
            putExtra(com.securecall.app.net.WebSocketService.EXTRA_FCM_PAYLOAD, payload)
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
            putExtra("sessionId", invite.sessionId)
            putExtra("callerClientId", invite.fromClientId)
            putExtra("callerPhone", invite.callerPhone)
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
            invite.sessionId, callerName, invite.fromClientId, invite.callerPhone,
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

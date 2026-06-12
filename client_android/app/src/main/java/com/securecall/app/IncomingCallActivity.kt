package com.securecall.app

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class IncomingCallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "INCOMING_CALL"

        @Volatile
        var activeInstance: IncomingCallActivity? = null

        /** Stop ringtone+vibration on the active instance from any thread. */
        fun stopActiveAudio() {
            val activity = activeInstance ?: return
            activity.runOnUiThread { activity.stopRingtoneAndVibration() }
        }

        /** Called by WebSocketService when CALL_END arrives during ringing. */
        fun dismissIfActive(sessionId: String) {
            val activity = activeInstance ?: return
            if (activity.sessionId == sessionId || sessionId.isEmpty()) {
                Log.d(TAG, "Caller cancelled call — auto-dismissing")
                activity.cancelRingTimeout()
                activity.saveMissedCall()
                activity.dismissIncomingCallNotification()
                activity.runOnUiThread {
                    activity.stopRingtoneAndVibration()
                    activity.finish()
                }
            }
        }
    }

    private var sessionId: String = ""
    private var callerClientId: String = ""
    private var callerPhone: String = ""
    private var callerDisplayName: String = ""
    private var accepted = false
    private var fromFcm = false // BUG-010: true when launched from FCM without WS
    private var ringTimeoutHandler: android.os.Handler? = null
    private var ringTimeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE: prevent screenshots of incoming call screen
        com.securecall.app.security.WindowSecurityHelper.applyFlagSecure(this)

        // Show over lock screen and wake device for incoming calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_incoming_call)
        activeInstance = this

        sessionId = intent.getStringExtra("sessionId") ?: ""
        callerClientId = intent.getStringExtra("callerClientId") ?: ""
        callerPhone = intent.getStringExtra("callerPhone") ?: ""
        fromFcm = intent.getBooleanExtra("from_fcm", false)
        Log.d(TAG, "Incoming call: session=$sessionId, from=$callerClientId, phone=$callerPhone, fromFcm=$fromFcm")

        // Check if this call was already cancelled before we launched
        // BUG-010: Skip this check when launched from FCM — WS may not be connected yet,
        // so getCurrentSessionId() will be null. That's expected, not a cancelled call.
        val ws = com.securecall.app.net.WebSocketService.instance
        if (!fromFcm && ws?.getCurrentSessionId() == null && sessionId.isNotEmpty()) {
            Log.d(TAG, "Call already cancelled before IncomingCallActivity created")
            saveMissedCallFromIntent()
            dismissIncomingCallNotification()
            finish()
            return
        }

        // Resolve caller name: phone book first, then SecureCall contacts, then fallback
        callerDisplayName = com.securecall.app.data.PhoneBookResolver.resolveCallerName(this, callerClientId, callerPhone)
        Log.d(TAG, "Caller display name: $callerDisplayName")

        val nameLabel = findViewById<TextView>(R.id.incomingCallerName)
        nameLabel.text = callerDisplayName

        // Color the name: green if known contact, red if first-time/unknown
        val isKnownContact = callerDisplayName != callerClientId && callerDisplayName != callerPhone
        if (isKnownContact) {
            nameLabel.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.caller_known_green))
        } else {
            nameLabel.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.caller_unknown_red))
        }

        // Show phone number subtitle if resolved name differs from phone number
        val phoneSubtitle = findViewById<TextView>(R.id.incomingCallerPhone)
        if (callerPhone.isNotEmpty() && callerDisplayName != callerPhone) {
            phoneSubtitle.text = callerPhone
            phoneSubtitle.visibility = android.view.View.VISIBLE
        }

        // Show SecureCall ID if it's a registered user
        val secureIdLabel = findViewById<TextView>(R.id.incomingCallerSecureId)
        if (callerClientId.startsWith("android-")) {
            secureIdLabel.text = "Secure ID: $callerClientId"
            secureIdLabel.visibility = android.view.View.VISIBLE
        }

        findViewById<FloatingActionButton>(R.id.fabAcceptCall).setOnClickListener { acceptCall() }
        findViewById<FloatingActionButton>(R.id.fabDeclineCall).setOnClickListener { declineCall() }

        // Ringtone+vibration is managed by WebSocketService (started before activity launch)

        // Ring timeout: auto-decline after 60 seconds to give callee time to answer
        ringTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        ringTimeoutRunnable = Runnable {
            Log.d(TAG, "Ring timeout (60s) — auto-declining")
            declineCall()
        }
        ringTimeoutHandler?.postDelayed(ringTimeoutRunnable!!, 60000)

        // Backup: also use callback for caller hangup during ringing
        ws?.setOnCallEnded { endedSessionId ->
            if (endedSessionId == sessionId) {
                Log.d(TAG, "Caller ended call while ringing (callback)")
                saveMissedCall()
                dismissIncomingCallNotification()
                runOnUiThread {
                    stopRingtoneAndVibration()
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // fullScreenIntent may re-deliver the same intent — just ignore it
        Log.d(TAG, "onNewIntent (ignored, already ringing)")
    }

    private fun dismissIncomingCallNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(1002) // INCOMING_CALL_NOTIFICATION_ID
    }

    private fun cancelRingTimeout() {
        ringTimeoutRunnable?.let { ringTimeoutHandler?.removeCallbacks(it) }
        ringTimeoutHandler = null
        ringTimeoutRunnable = null
    }

    private fun acceptCall() {
        accepted = true
        cancelRingTimeout()
        stopRingtoneAndVibration()
        dismissIncomingCallNotification()
        Log.d(TAG, "Accepting call, session=$sessionId, fromFcm=$fromFcm")

        val ws = com.securecall.app.net.WebSocketService.instance

        // If launched from FCM, WS may be connected but not REGISTERED yet.
        // The backend rejects CALL_ACCEPT until REGISTERED, so wait for both states.
        if (fromFcm && (ws == null || !ws.isConnected || !ws.isRegistered)) {
            Log.d(TAG, "FCM accept — waiting for WS registration before CALL_ACCEPT")
            com.securecall.app.debug.SecLogManager.log("CALL", "FCM accept — waiting for WS registration")
            waitForWsAndAccept()
        } else {
            ws?.sendCallAccept(sessionId)
            launchCallActivity()
        }
    }

    /**
     * Wait for WS to reconnect and receive REGISTERED, then send CALL_ACCEPT.
     * Polls every 500ms. If timeout, queue CALL_ACCEPT and launch the call UI.
     */
    private fun waitForWsAndAccept() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val checkRunnable = object : Runnable {
            override fun run() {
                val ws = com.securecall.app.net.WebSocketService.instance
                val elapsed = System.currentTimeMillis() - startTime
                if (ws != null && ws.isConnected && ws.isRegistered) {
                    Log.d(TAG, "WS registered after ${elapsed}ms — sending CALL_ACCEPT")
                    com.securecall.app.debug.SecLogManager.log("CALL", "WS registered after ${elapsed}ms — sending CALL_ACCEPT")
                    ws.sendCallAccept(sessionId)
                    launchCallActivity()
                } else if (elapsed > 15_000) {
                    Log.w(TAG, "WS registration timeout (15s) — queueing CALL_ACCEPT and launching CallActivity")
                    com.securecall.app.debug.SecLogManager.log("CALL", "WS registration timeout — CALL_ACCEPT queued")
                    ws?.sendCallAccept(sessionId)
                    launchCallActivity()
                } else {
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.postDelayed(checkRunnable, 500)
    }

    private fun launchCallActivity() {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("sessionId", sessionId)
            putExtra("callerName", callerDisplayName)
            putExtra("phoneNumber", callerClientId)
            putExtra("originalPhone", callerPhone)
            putExtra("isIncoming", true)
        }
        startActivity(intent)
        finish()
    }

    private fun declineCall() {
        cancelRingTimeout()
        stopRingtoneAndVibration()
        dismissIncomingCallNotification()
        Log.d(TAG, "Declining call, session=$sessionId")
        saveMissedCall()
        val ws = com.securecall.app.net.WebSocketService.instance
        if (sessionId.isNotEmpty()) ws?.sendCallEnd(sessionId)
        ws?.clearSession() // BUG-032: clear session so next call isn't rejected as BUSY
        finish()
    }

    private fun saveMissedCall() {
        val saveHistory = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pref_call_history", true)
        if (!saveHistory) {
            Log.d(TAG, "Call history saving disabled by user preference")
            return
        }
        try {
            val record = com.securecall.app.data.CallRecord(
                contactName = callerDisplayName,
                contactId = callerClientId,
                type = com.securecall.app.data.CallType.MISSED,
                durationSeconds = 0,
                phoneNumber = callerPhone.ifEmpty { null } // BUG-013: save phone for name re-resolution
            )
            com.securecall.app.data.CallHistoryRepository.add(this, record)
            Log.d(TAG, "Missed call saved: $callerDisplayName (phone=$callerPhone)")
            postMissedCallNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save missed call", e)
        }
    }

    private fun postMissedCallNotification() {
        val channelId = "securecall_missed_calls"
        val groupKey = "securecall_missed_calls_group"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for missed SecureCall calls"
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
        val openIntent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        // Unique notification ID per missed call so launcher badge count reflects actual count
        val prefs = getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
        val nextId = prefs.getInt("missed_notif_next_id", 2000)
        prefs.edit().putInt("missed_notif_next_id", nextId + 1).apply()
        // Track active notification IDs for bulk cancel when user views Calls tab
        val activeIds = prefs.getString("missed_notif_ids", "") ?: ""
        val updatedIds = if (activeIds.isEmpty()) "$nextId" else "$activeIds,$nextId"
        prefs.edit().putString("missed_notif_ids", updatedIds).apply()

        val missedCount = com.securecall.app.data.CallHistoryRepository.countMissed(this)
        // Individual notification for this missed call
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(R.string.missed_call_title))
            .setContentText(callerDisplayName)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(groupKey)
            .setNumber(missedCount)
            .build()
        nm.notify(nextId, notification)
        // Group summary notification (required for grouped notifications on Android 7+)
        val summary = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(R.string.missed_call_title))
            .setContentText("$missedCount missed calls")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setNumber(missedCount)
            .build()
        nm.notify(1003, summary)
        Log.d(TAG, "Missed call notification posted for $callerDisplayName (id=$nextId, total=$missedCount)")
    }

    /** Save missed call when dismissing due to race condition (before contact name resolved). */
    private fun saveMissedCallFromIntent() {
        val callerPhone = intent.getStringExtra("callerPhone") ?: ""
        callerDisplayName = com.securecall.app.data.PhoneBookResolver.resolveCallerName(this, callerClientId, callerPhone)
        saveMissedCall()
    }

    /** Stop ringtone+vibration — delegates to WebSocketService which owns the audio. */
    private fun stopRingtoneAndVibration() {
        com.securecall.app.net.WebSocketService.instance?.stopIncomingRingtone()
    }

    override fun onDestroy() {
        cancelRingTimeout()
        stopRingtoneAndVibration()
        // Only clear activeInstance if WE are the current instance (avoids race with new instance)
        if (activeInstance === this) {
            activeInstance = null
        }
        // Only clear callback if we didn't accept — CallActivity sets its own onCallEnded
        if (!accepted) {
            com.securecall.app.net.WebSocketService.instance?.setOnCallEnded(null)
        }
        super.onDestroy()
    }
}

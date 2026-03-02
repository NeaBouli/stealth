package com.securecall.app

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringTimeoutHandler: android.os.Handler? = null
    private var ringTimeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        Log.d(TAG, "Incoming call: session=$sessionId, from=$callerClientId, phone=$callerPhone")

        // Check if this call was already cancelled before we launched
        val ws = com.securecall.app.net.WebSocketService.instance
        if (ws?.getCurrentSessionId() == null && sessionId.isNotEmpty()) {
            Log.d(TAG, "Call already cancelled before IncomingCallActivity created")
            saveMissedCallFromIntent()
            dismissIncomingCallNotification()
            finish()
            return
        }

        // Resolve caller name: phone book first, then SecureCall contacts, then fallback
        callerDisplayName = com.securecall.app.data.PhoneBookResolver.resolveCallerName(this, callerClientId, callerPhone)
        Log.d(TAG, "Caller display name: $callerDisplayName")

        findViewById<TextView>(R.id.incomingCallerName).text = callerDisplayName

        // Show phone number subtitle if resolved name differs from phone number
        val phoneSubtitle = findViewById<TextView>(R.id.incomingCallerPhone)
        if (callerPhone.isNotEmpty() && callerDisplayName != callerPhone) {
            phoneSubtitle.text = callerPhone
            phoneSubtitle.visibility = android.view.View.VISIBLE
        }

        findViewById<FloatingActionButton>(R.id.fabAcceptCall).setOnClickListener { acceptCall() }
        findViewById<FloatingActionButton>(R.id.fabDeclineCall).setOnClickListener { declineCall() }

        // Start ringtone and vibration
        startRingtone()
        startVibration()

        // Ring timeout: auto-decline after 45 seconds to prevent infinite ringing
        ringTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        ringTimeoutRunnable = Runnable {
            Log.d(TAG, "Ring timeout (45s) — auto-declining")
            declineCall()
        }
        ringTimeoutHandler?.postDelayed(ringTimeoutRunnable!!, 45000)

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
        Log.d(TAG, "Accepting call, session=$sessionId")
        val ws = com.securecall.app.net.WebSocketService.instance
        ws?.sendCallAccept(sessionId)

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
        finish()
    }

    private fun saveMissedCall() {
        try {
            val record = com.securecall.app.data.CallRecord(
                contactName = callerDisplayName,
                contactId = callerClientId,
                type = com.securecall.app.data.CallType.MISSED,
                durationSeconds = 0
            )
            com.securecall.app.data.CallHistoryRepository.add(this, record)
            Log.d(TAG, "Missed call saved: $callerDisplayName")
            postMissedCallNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save missed call", e)
        }
    }

    private fun postMissedCallNotification() {
        val channelId = "securecall_missed_calls"
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
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(R.string.missed_call_title))
            .setContentText(callerDisplayName)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setNumber(com.securecall.app.data.CallHistoryRepository.countMissed(this))
            .build()
        nm.notify(1003, notification)
        Log.d(TAG, "Missed call notification posted for $callerDisplayName")
    }

    /** Save missed call when dismissing due to race condition (before contact name resolved). */
    private fun saveMissedCallFromIntent() {
        val callerPhone = intent.getStringExtra("callerPhone") ?: ""
        callerDisplayName = com.securecall.app.data.PhoneBookResolver.resolveCallerName(this, callerClientId, callerPhone)
        saveMissedCall()
    }

    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@IncomingCallActivity, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "Ringtone started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // Pattern: wait 0ms, vibrate 1000ms, pause 1000ms — repeat
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun stopRingtoneAndVibration() {
        val player = ringtonePlayer
        ringtonePlayer = null
        if (player != null) {
            try { player.stop() } catch (e: Exception) { Log.e(TAG, "Error stopping ringtone", e) }
            try { player.release() } catch (e: Exception) { Log.e(TAG, "Error releasing ringtone", e) }
            Log.d(TAG, "Ringtone stopped and released")
        }
        val vib = vibrator
        vibrator = null
        if (vib != null) {
            try { vib.cancel() } catch (e: Exception) { Log.e(TAG, "Error stopping vibration", e) }
            Log.d(TAG, "Vibration cancelled")
        }
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

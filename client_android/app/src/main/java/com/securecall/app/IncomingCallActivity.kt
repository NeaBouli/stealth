package com.securecall.app

import android.app.KeyguardManager
import android.app.NotificationManager
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

        /** Called by WebSocketService when CALL_END arrives during ringing. */
        fun dismissIfActive(sessionId: String) {
            val activity = activeInstance ?: return
            if (activity.sessionId == sessionId) {
                Log.d(TAG, "Caller cancelled call — auto-dismissing")
                activity.saveMissedCall()
                activity.dismissIncomingCallNotification()
                activity.runOnUiThread { activity.finish() }
            }
        }
    }

    private var sessionId: String = ""
    private var callerClientId: String = ""
    private var callerDisplayName: String = ""
    private var accepted = false

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
        Log.d(TAG, "Incoming call: session=$sessionId, from=$callerClientId")

        // Check if this call was already cancelled before we launched
        val ws = com.securecall.app.net.WebSocketService.instance
        if (ws?.getCurrentSessionId() == null && sessionId.isNotEmpty()) {
            Log.d(TAG, "Call already cancelled before IncomingCallActivity created")
            saveMissedCallFromIntent()
            dismissIncomingCallNotification()
            finish()
            return
        }

        // Look up caller's clientId in contacts to show saved name
        callerDisplayName = com.securecall.app.data.ContactRepository.getAll(this)
            .find { it.phoneOrId == callerClientId }?.name ?: callerClientId
        Log.d(TAG, "Caller display name: $callerDisplayName")

        findViewById<TextView>(R.id.incomingCallerName).text = callerDisplayName

        findViewById<FloatingActionButton>(R.id.fabAcceptCall).setOnClickListener { acceptCall() }
        findViewById<FloatingActionButton>(R.id.fabDeclineCall).setOnClickListener { declineCall() }

        // Backup: also use callback for caller hangup during ringing
        ws?.setOnCallEnded { endedSessionId ->
            if (endedSessionId == sessionId) {
                Log.d(TAG, "Caller ended call while ringing (callback)")
                saveMissedCall()
                dismissIncomingCallNotification()
                runOnUiThread { finish() }
            }
        }
    }

    private fun dismissIncomingCallNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(1002) // INCOMING_CALL_NOTIFICATION_ID
    }

    private fun acceptCall() {
        accepted = true
        dismissIncomingCallNotification()
        Log.d(TAG, "Accepting call, session=$sessionId")
        val ws = com.securecall.app.net.WebSocketService.instance
        ws?.sendCallAccept(sessionId)

        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("sessionId", sessionId)
            putExtra("callerName", callerDisplayName)
            putExtra("phoneNumber", callerClientId)
            putExtra("isIncoming", true)
        }
        startActivity(intent)
        finish()
    }

    private fun declineCall() {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save missed call", e)
        }
    }

    /** Save missed call when dismissing due to race condition (before contact name resolved). */
    private fun saveMissedCallFromIntent() {
        val name = com.securecall.app.data.ContactRepository.getAll(this)
            .find { it.phoneOrId == callerClientId }?.name ?: callerClientId
        callerDisplayName = name
        saveMissedCall()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only clear activeInstance if WE are the current instance (avoids race with new instance)
        if (activeInstance === this) {
            activeInstance = null
        }
        // Only clear callback if we didn't accept — CallActivity sets its own onCallEnded
        if (!accepted) {
            com.securecall.app.net.WebSocketService.instance?.setOnCallEnded(null)
        }
    }
}

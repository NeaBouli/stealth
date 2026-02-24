package com.securecall.app

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class IncomingCallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "INCOMING_CALL"
    }

    private var sessionId: String = ""
    private var callerClientId: String = ""
    private var callerDisplayName: String = ""
    private var accepted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        sessionId = intent.getStringExtra("sessionId") ?: ""
        callerClientId = intent.getStringExtra("callerClientId") ?: ""
        Log.d(TAG, "Incoming call: session=$sessionId, from=$callerClientId")

        // Look up caller's clientId in contacts to show saved name
        callerDisplayName = com.securecall.app.data.ContactRepository.getAll(this)
            .find { it.phoneOrId == callerClientId }?.name ?: callerClientId
        Log.d(TAG, "Caller display name: $callerDisplayName")

        findViewById<TextView>(R.id.incomingCallerName).text = callerDisplayName

        findViewById<FloatingActionButton>(R.id.fabAcceptCall).setOnClickListener { acceptCall() }
        findViewById<FloatingActionButton>(R.id.fabDeclineCall).setOnClickListener { declineCall() }

        // If caller hangs up while ringing, close this screen
        val ws = com.securecall.app.net.WebSocketService.instance
        ws?.setOnCallEnded { endedSessionId ->
            if (endedSessionId == sessionId) {
                Log.d(TAG, "Caller ended call while ringing")
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

    override fun onDestroy() {
        super.onDestroy()
        // Only clear callback if we didn't accept — CallActivity sets its own onCallEnded
        if (!accepted) {
            com.securecall.app.net.WebSocketService.instance?.setOnCallEnded(null)
        }
    }
}

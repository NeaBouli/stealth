package com.securecall.app

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        sessionId = intent.getStringExtra("sessionId") ?: ""
        callerClientId = intent.getStringExtra("callerClientId") ?: ""
        Log.d(TAG, "Incoming call: session=$sessionId, from=$callerClientId")

        findViewById<TextView>(R.id.incomingCallerName).text = callerClientId

        findViewById<FloatingActionButton>(R.id.fabAcceptCall).setOnClickListener { acceptCall() }
        findViewById<FloatingActionButton>(R.id.fabDeclineCall).setOnClickListener { declineCall() }

        // If caller hangs up while ringing, close this screen
        val ws = com.securecall.app.net.WebSocketService.instance
        ws?.setOnCallEnded { endedSessionId ->
            if (endedSessionId == sessionId) {
                Log.d(TAG, "Caller ended call while ringing")
                runOnUiThread { finish() }
            }
        }
    }

    private fun acceptCall() {
        Log.d(TAG, "Accepting call, session=$sessionId")
        val ws = com.securecall.app.net.WebSocketService.instance
        ws?.sendCallAccept(sessionId)

        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("sessionId", sessionId)
            putExtra("callerName", callerClientId)
            putExtra("isIncoming", true)
        }
        startActivity(intent)
        finish()
    }

    private fun declineCall() {
        Log.d(TAG, "Declining call, session=$sessionId")
        val ws = com.securecall.app.net.WebSocketService.instance
        if (sessionId.isNotEmpty()) ws?.sendCallEnd(sessionId)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        com.securecall.app.net.WebSocketService.instance?.setOnCallEnded(null)
    }
}

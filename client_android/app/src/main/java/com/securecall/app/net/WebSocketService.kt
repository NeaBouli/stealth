package com.securecall.app.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.securecall.app.BuildConfig
import com.securecall.app.MainActivity
import com.securecall.app.R

/**
 * BACKEND-22..58 / PATCH 201..204:
 * WebSocketService — hält die WS-Verbindung zum Signaling-Server.
 */
class WebSocketService : Service(), HeartbeatClient.Listener {

    private val binder = LocalBinder()
    private var client: HeartbeatClient? = null
    private val wsUrl: String = BuildConfig.SIGNAL_WS_URL

    // BACKEND-22: Status-Callbacks
    var statusCallbackOnline: (() -> Unit)? = null
    var statusCallbackOffline: (() -> Unit)? = null
    var errorCallback: ((Throwable) -> Unit)? = null

    // BACKEND-25: Heartbeat Überwachung
    private val heartbeatIntervalMs = 5000L
    private val heartbeatTimeoutMs = 15000L
    private var heartbeatTimer: java.util.Timer? = null

    // BACKEND-26: Reconnect-Backoff
    private var reconnectAttempts = 0
    private val backoffSequenceMs = longArrayOf(1000L, 3000L, 5000L)

    inner class LocalBinder : Binder() {
        fun getService(): WebSocketService = this@WebSocketService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    companion object {
        @Volatile
        var instance: WebSocketService? = null
        private const val CHANNEL_ID = "securecall_foreground"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WS_SERVICE", "onCreate")
        instance = this
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("pref_background_service", true)) {
            startForegroundWithNotification()
        }
        client = HeartbeatClient(wsUrl, this)
        client?.connect()
    }

    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy")
        instance = null
        stopHeartbeatMonitor()
        client?.close()
        super.onDestroy()
    }

    // ===================== Public API =====================

    fun sendMessage(text: String) {
        client?.send(text)
    }

    fun lastSeen(): Long {
        return client?.getLastSeen() ?: 0L
    }

    // ===================== HeartbeatClient.Listener =====================

    override fun onConnected() {
        Log.d("WS_SERVICE", "WebSocket connected")
        reconnectAttempts = 0
        startHeartbeatMonitor()
        registerClient()
        statusCallbackOnline?.invoke()
    }

    private fun registerClient() {
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        var clientId = prefs.getString("client_id", null)
        if (clientId == null) {
            clientId = "android-" + java.util.UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString("client_id", clientId).apply()
            Log.d("WS_SERVICE", "Generated new clientId: $clientId")
        }
        val json = """{"type":"REGISTER","clientId":"$clientId"}"""
        client?.send(json)
        Log.d("WS_SERVICE", "REGISTER sent: $clientId")
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected")
        stopHeartbeatMonitor()
        statusCallbackOffline?.invoke()
        scheduleReconnect()
    }

    override fun onMessage(text: String) {
        Log.d("WS_SERVICE", "Message: $text")
        handleIncomingMessageFull(text)
    }

    override fun onError(t: Throwable) {
        Log.e("WS_SERVICE", "WebSocket error", t)
        errorCallback?.invoke(t)
        statusCallbackOffline?.invoke()
        // Reconnect is handled by HeartbeatClient — do NOT call scheduleReconnect() here
    }

    override fun onPing() {
        Log.d("WS_SERVICE", "Ping received")
    }

    override fun onPong() {
        Log.d("WS_SERVICE", "Pong received")
    }

    // ===================== Reconnect =====================

    private fun scheduleReconnect() {
        val delay = backoffSequenceMs[
            if (reconnectAttempts >= backoffSequenceMs.size) backoffSequenceMs.size - 1
            else reconnectAttempts
        ]
        Log.w("WS_SERVICE", "Scheduling reconnect in ${delay}ms (attempt $reconnectAttempts)")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            reconnectAttempts++
            connectWebSocket()
        }, delay)
    }

    private fun connectWebSocket() {
        Log.d("WS_SERVICE", "connectWebSocket() invoked")
        client?.connect()
    }

    fun forceReconnect() {
        Log.w("WS_SERVICE", "ForceReconnect() invoked — closing WS + scheduling reconnect")
        try {
            client?.close()
        } catch (_: Exception) {}
        stopHeartbeatMonitor()
        statusCallbackOffline?.invoke()
        scheduleReconnect()
    }

    // BACKEND-56: Debug-Funktion zum künstlichen Trennen
    fun forceDisconnectForDebug() {
        Log.w("WS_SERVICE", "Force-disconnect triggered (debug)")
        client?.close()
    }

    // BACKEND-57: Reconnect-Flow
    fun reconnectFlow(trigger: String) {
        Log.w("WS_SERVICE", "Starting reconnectFlow() — trigger=$trigger")
        try {
            com.securecall.app.ghostnet.transport.GhostTransport.stop()
        } catch (_: Throwable) {}
        try {
            client?.close()
        } catch (_: Throwable) {}
        client?.connect()
        Log.d("WS_SERVICE", "reconnectFlow() completed")
    }

    // ===================== Heartbeat Monitor =====================

    private fun startHeartbeatMonitor() {
        heartbeatTimer?.cancel()
        heartbeatTimer = java.util.Timer()
        heartbeatTimer?.schedule(object : java.util.TimerTask() {
            override fun run() {
                val last = client?.getLastSeen() ?: 0
                val now = System.currentTimeMillis()
                if (now - last > heartbeatTimeoutMs) {
                    Log.w("WS_SERVICE", "Heartbeat timeout → treating as disconnect")
                    handleHeartbeatTimeout()
                }
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs)
    }

    private fun stopHeartbeatMonitor() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    private fun handleHeartbeatTimeout() {
        statusCallbackOffline?.invoke()
        try {
            client?.close()
        } catch (_: Exception) {}
    }

    // ===================== Call Signaling =====================

    fun sendCallInvite(targetId: String) {
        val json = """
            {
              "type": "CALL_INVITE",
              "to": "$targetId"
            }
        """.trimIndent()
        client?.send(json)
        Log.d("WS_SERVICE", "CALL_INVITE sent to $targetId")
    }

    fun sendCallAccept(sessionId: String) {
        val json = """
            {
              "type": "CALL_ACCEPT",
              "sessionId": "$sessionId"
            }
        """.trimIndent()
        client?.send(json)
        Log.d("WS_SERVICE", "CALL_ACCEPT sent for session $sessionId")
    }

    fun sendCallEnd(sessionId: String) {
        val json = """
            {
              "type": "CALL_END",
              "sessionId": "$sessionId"
            }
        """.trimIndent()
        client?.send(json)
        Log.d("WS_SERVICE", "CALL_END sent for session $sessionId")
    }

    // BACKEND-23: GHOST_PREPARE senden
    fun sendGhostPrepare(sessionId: String) {
        val keyMaterial = com.securecall.app.crypto.EphemeralKeyProvider.generateKeyMaterialBase64()
        val json = """
            {
              "type": "GHOST_PREPARE",
              "sessionId": "$sessionId",
              "clientId": "android-client",
              "keyMaterial": "$keyMaterial"
            }
        """.trimIndent()
        client?.send(json)
        Log.d("WS_SERVICE", "GHOST_PREPARE sent for $sessionId")
    }

    // ===================== Incoming Message Handling =====================

    private fun handleIncomingMessageFull(json: String) {
        // Key-Exchange
        try {
            val parsedKey = com.securecall.app.net.signal.SignalParser.parse(json)
            if (parsedKey != null) {
                handleKeyExchange(json)
                return
            }
        } catch (_: Throwable) {}

        // Call-Signaling
        try {
            val parsedCall = com.securecall.app.net.signal.CallSignalParser.parse(json)
            if (parsedCall != null) {
                val (type, callId) = parsedCall
                when (type) {
                    "call-init" -> {
                        Log.d("WS_SERVICE", "Received call-init: $callId")
                        com.securecall.app.call.CallController.incomingCall(callId)
                    }
                    "call-bye" -> {
                        Log.d("WS_SERVICE", "Received call-bye: $callId")
                        com.securecall.app.call.CallController.endCall()
                    }
                    else -> {
                        Log.w("WS_SERVICE", "Unknown call-signal type: $type")
                    }
                }
                return
            }
        } catch (_: Throwable) {}

        // Subscription verification
        try {
            val obj = org.json.JSONObject(json)
            if (obj.optString("type") == "SUBSCRIPTION_VERIFY_ACK") {
                handleSubscriptionVerifyAck(obj)
                return
            }
        } catch (_: Throwable) {}

        // Standard signaling
        handleIncomingMessage(json)
        handleIncomingCallEnd(json)
        handleGhostAck(json)
    }

    private fun handleIncomingMessage(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            when (obj.getString("type")) {
                "CALL_INVITE" -> {
                    val sessionId = obj.optString("sessionId", "")
                    Log.d("WS_SERVICE", "Incoming CALL_INVITE, sessionId=$sessionId")
                }
                "CALL_ACCEPT" -> {
                    Log.d("WS_SERVICE", "Remote accepted call")
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleIncomingCallEnd(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            if (obj.getString("type") == "CALL_END") {
                Log.d("WS_SERVICE", "CALL_END received from remote")
            }
        } catch (_: Exception) {}
    }

    // BACKEND-24: GHOST_ACK handling
    private fun handleGhostAck(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            if (obj.getString("type") == "GHOST_ACK") {
                val id = obj.getString("ghostNetId")
                Log.d("WS_SERVICE", "GHOST_ACK received → ghostNetId=$id")
            }
        } catch (_: Exception) {}
    }

    // PATCH 201: Key-Exchange handling
    private fun handleKeyExchange(json: String) {
        try {
            val parsed = com.securecall.app.net.signal.SignalParser.parse(json) ?: return
            val (type, remotePub) = parsed
            when (type) {
                "key-offer" -> {
                    Log.d("WS_SERVICE", "Received key-offer → sending key-answer")
                    val localPub = com.securecall.app.ghostnet.keys.GhostNetKeyMaterial.getLocalPub()
                    val answer = com.securecall.app.net.signal.KeyAnswer(localPub).toJson()
                    client?.send(answer)
                    com.securecall.app.ghostnet.handshake.HandshakeController.acceptIncoming(remotePub)
                }
                "key-answer" -> {
                    Log.d("WS_SERVICE", "Received key-answer → completing handshake")
                    com.securecall.app.ghostnet.handshake.HandshakeController.startOutgoing(remotePub)
                }
            }
        } catch (t: Throwable) {
            Log.e("WS_SERVICE", "handleKeyExchange() failed", t)
        }
    }

    // Phase 6: SUBSCRIPTION_VERIFY_ACK handling
    private fun handleSubscriptionVerifyAck(obj: org.json.JSONObject) {
        try {
            val tierStr = obj.getString("tier")
            val expiresAt = obj.optLong("expiresAt", 0L)
            val tier = com.securecall.app.billing.SubscriptionTier.fromName(tierStr)
            Log.d("WS_SERVICE", "SUBSCRIPTION_VERIFY_ACK: tier=$tierStr, expiresAt=$expiresAt")

            val ctx = applicationContext
            val manager = com.securecall.app.billing.SubscriptionManager(ctx)
            manager.updateFromServerVerification(tier, expiresAt)
        } catch (t: Throwable) {
            Log.e("WS_SERVICE", "handleSubscriptionVerifyAck() failed", t)
        }
    }

    // BACKEND-58: Transport Re-Init nach erfolgreichem WS-Connect
    private fun reinitTransportLayer() {
        try {
            com.securecall.app.ghostnet.transport.GhostTransport.start()
        } catch (t: Throwable) {
            Log.e("WS_SERVICE", "Transport reinit failed", t)
        }
    }

    fun updateForegroundMode(enabled: Boolean) {
        if (enabled) {
            startForegroundWithNotification()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_background),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_background_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_background_title))
            .setContentText(getString(R.string.notif_background_text))
            .setSmallIcon(R.drawable.ic_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}

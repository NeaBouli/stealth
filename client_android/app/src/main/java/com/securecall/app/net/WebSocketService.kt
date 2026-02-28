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

    // Call signaling state and callbacks (private backing fields)
    private var _currentSessionId: String? = null
    private var _onIncomingCall: ((String, String) -> Unit)? = null
    private var _onCallAccepted: ((String) -> Unit)? = null
    private var _onCallEnded: ((String) -> Unit)? = null
    private var _onCallError: ((String, String) -> Unit)? = null

    // Audio playback pipeline
    private var audioPlayer: com.securecall.app.ghostnet.media.playback.GhostAudioPlayer? = null
    private var opusDecoderInitialized = false
    private var jitterPlaybackThread: Thread? = null
    @Volatile private var jitterPlaybackRunning = false

    // E2E encryption state (X25519 + XChaCha20-Poly1305)
    private var localPrivKey: ByteArray? = null
    private var remotePubKey: ByteArray? = null
    private var sessionKey: ByteArray? = null

    // WebRTC P2P DataChannel transport
    private var webRtcManager: WebRtcManager? = null

    // Phone lookup callback
    private var _phoneLookupCallback: ((String?) -> Unit)? = null
    // Batch phone lookup callback: returns set of registered phone numbers
    private var _batchPhoneLookupCallback: ((Set<String>) -> Unit)? = null

    fun getCurrentSessionId(): String? = _currentSessionId
    fun setOnCallAccepted(cb: ((String) -> Unit)?) { _onCallAccepted = cb }
    fun setOnCallEnded(cb: ((String) -> Unit)?) { _onCallEnded = cb }
    fun setOnCallError(cb: ((String, String) -> Unit)?) { _onCallError = cb }
    fun clearSession() {
        _currentSessionId = null
        localPrivKey?.fill(0)
        localPrivKey = null
        remotePubKey = null
        sessionKey?.fill(0)
        sessionKey = null
        webRtcManager?.close()
        webRtcManager = null
        killAllAudio()
    }

    /** Stop ALL audio resources globally — ringtone, ringback, playback. Belt-and-suspenders. */
    fun killAllAudio() {
        Log.d("WS_SERVICE", "killAllAudio() — stopping all audio resources")
        try { stopAudioPlayback() } catch (e: Exception) { Log.e("WS_SERVICE", "Error in stopAudioPlayback", e) }
        // Dismiss IncomingCallActivity ringtone+vibration if still active
        try {
            com.securecall.app.IncomingCallActivity.stopActiveAudio()
        } catch (e: Exception) { Log.e("WS_SERVICE", "Error stopping IncomingCallActivity audio", e) }
        // Stop CallActivity ringback tone if still playing
        try {
            com.securecall.app.CallActivity.stopActiveAudio()
        } catch (e: Exception) { Log.e("WS_SERVICE", "Error stopping CallActivity audio", e) }
    }

    fun getLocalClientId(): String? {
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        return prefs.getString("client_id", null)
    }

    // BACKEND-25: Heartbeat Überwachung
    private val heartbeatIntervalMs = 5000L
    private val heartbeatTimeoutMs = 30000L
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
        private const val CHANNEL_INCOMING = "securecall_incoming_call"
        private const val NOTIFICATION_ID = 1001
        private const val INCOMING_CALL_NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WS_SERVICE", "onCreate")
        instance = this
        // Always start as foreground to survive background/kill
        startForegroundWithNotification()
        createIncomingCallChannel()
        client = HeartbeatClient(wsUrl, this)
        client?.connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: system restarts service if killed
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("WS_SERVICE", "App swiped away — service continues in background")
    }

    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy")
        instance = null
        stopHeartbeatMonitor()
        stopAudioPlayback()
        client?.close()
        super.onDestroy()
    }

    // ===================== Public API =====================

    fun sendMessage(text: String) {
        client?.send(text)
    }

    fun sendBinary(data: ByteArray): Boolean {
        val key = sessionKey
        val toSend = if (key != null && com.securecall.crypto.CoreCrypto.isNativeAvailable()) {
            com.securecall.crypto.CoreCrypto.encrypt(key, data) ?: data
        } else {
            data
        }
        // Send via P2P DataChannel only — no WS relay fallback.
        // During call setup, audio frames before DataChannel opens would
        // flood the signaling WebSocket and trigger server rate limits.
        val rtc = webRtcManager
        if (rtc != null && rtc.isDataChannelOpen) {
            return rtc.send(toSend)
        }
        return false
    }

    fun lastSeen(): Long {
        return client?.getLastSeen() ?: 0L
    }

    /** Look up a phone number on the server to resolve it to a clientId. */
    fun lookupPhone(phoneNumber: String, callback: (clientId: String?) -> Unit) {
        _phoneLookupCallback = callback
        val json = """{"type":"PHONE_LOOKUP","phoneNumber":"$phoneNumber"}"""
        val sent = client?.send(json) ?: false
        if (!sent) {
            Log.w("WS_SERVICE", "PHONE_LOOKUP failed to send (WS not connected)")
            _phoneLookupCallback = null
            callback(null)
            return
        }
        Log.d("WS_SERVICE", "PHONE_LOOKUP sent: $phoneNumber")
        // Timeout: if no response in 5 seconds, invoke callback with null
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val pending = _phoneLookupCallback
            if (pending === callback) {
                Log.w("WS_SERVICE", "PHONE_LOOKUP timeout for $phoneNumber")
                _phoneLookupCallback = null
                callback(null)
            }
        }, 5000)
    }

    /** Batch-check which phone numbers are registered SecureCall users. */
    fun batchPhoneLookup(phoneNumbers: List<String>, callback: (registered: Set<String>) -> Unit) {
        _batchPhoneLookupCallback = callback
        val arr = org.json.JSONArray(phoneNumbers)
        val json = org.json.JSONObject().apply {
            put("type", "BATCH_PHONE_LOOKUP")
            put("phoneNumbers", arr)
        }.toString()
        val sent = client?.send(json) ?: false
        if (!sent) {
            Log.w("WS_SERVICE", "BATCH_PHONE_LOOKUP failed to send")
            _batchPhoneLookupCallback = null
            callback(emptySet())
            return
        }
        Log.d("WS_SERVICE", "BATCH_PHONE_LOOKUP sent: ${phoneNumbers.size} numbers")
    }

    // ===================== HeartbeatClient.Listener =====================

    override fun onConnected() {
        Log.d("WS_SERVICE", "WebSocket connected")
        reconnectAttempts = 0
        startHeartbeatMonitor()
        registerClient()
        setupCallSignalingCallbacks()
        statusCallbackOnline?.invoke()
    }

    private fun setupCallSignalingCallbacks() {
        _onIncomingCall = { sessionId, fromClientId ->
            Log.d("WS_SERVICE", "Incoming call: session=$sessionId, from=$fromClientId")
            showIncomingCallNotification(sessionId, fromClientId)
        }
    }

    private fun showIncomingCallNotification(sessionId: String, fromClientId: String) {
        // Resolve caller name from contacts
        val callerName = com.securecall.app.data.ContactRepository.getAll(this)
            .find { it.phoneOrId == fromClientId }?.name ?: fromClientId

        val intent = android.content.Intent(this, com.securecall.app.IncomingCallActivity::class.java).apply {
            putExtra("sessionId", sessionId)
            putExtra("callerClientId", fromClientId)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // Always launch activity directly (works when app is in foreground or background)
        try {
            startActivity(intent)
            Log.d("WS_SERVICE", "IncomingCallActivity launched directly for $callerName")
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "Failed to launch IncomingCallActivity directly", e)
        }

        // Also show high-priority notification as backup (for lock screen / DND / Android 10+ restrictions)
        val fullScreenPending = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(R.string.incoming_call_title))
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPending, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
        Log.d("WS_SERVICE", "Incoming call notification shown for $callerName")
    }

    private fun createIncomingCallChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_INCOMING,
                "Incoming Calls",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming secure calls"
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun registerClient() {
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        var clientId = prefs.getString("client_id", null)
        if (clientId == null) {
            clientId = "android-" + java.util.UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString("client_id", clientId).apply()
            Log.d("WS_SERVICE", "Generated new clientId: $clientId")
        }
        // Read device phone number if permission is granted
        val phoneNumber = getDevicePhoneNumber()
        val json = if (phoneNumber != null) {
            """{"type":"REGISTER","clientId":"$clientId","phoneNumber":"$phoneNumber"}"""
        } else {
            """{"type":"REGISTER","clientId":"$clientId"}"""
        }
        client?.send(json)
        Log.d("WS_SERVICE", "REGISTER sent: $clientId, phone: ${phoneNumber ?: "none"}")
    }

    /** Re-register with the server (e.g. after manual phone number change in Settings). */
    fun reRegister() {
        registerClient()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun getDevicePhoneNumber(): String? {
        return try {
            val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                checkSelfPermission(android.Manifest.permission.READ_PHONE_NUMBERS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            // 1. Primary: TelephonyManager (reads SIM card)
            if (hasPermission) {
                val tm = getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                val number = tm?.line1Number
                if (!number.isNullOrBlank()) {
                    Log.d("WS_SERVICE", "Device phone number (SIM): $number")
                    return number
                }

                // 2. Secondary: SubscriptionManager (may return number when TelephonyManager doesn't)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    try {
                        val sm = android.telephony.SubscriptionManager.from(this)
                        val subList = sm.activeSubscriptionInfoList
                        if (subList != null) {
                            for (sub in subList) {
                                val subNumber = sub.number
                                if (!subNumber.isNullOrBlank()) {
                                    Log.d("WS_SERVICE", "Device phone number (SubscriptionManager): $subNumber")
                                    return subNumber
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("WS_SERVICE", "SubscriptionManager failed: ${e.message}")
                    }
                }
            } else {
                Log.d("WS_SERVICE", "No phone number permission")
            }

            // 3. Fallback: manual phone number (user entered once via prompt)
            val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
            val manualNumber = prefs.getString("manual_phone_number", null)
            if (!manualNumber.isNullOrBlank()) {
                Log.d("WS_SERVICE", "Device phone number (manual): $manualNumber")
                return manualNumber
            }

            Log.d("WS_SERVICE", "Device phone number unavailable (no SIM, no manual)")
            null
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "Failed to read phone number", e)
            null
        }
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected")
        stopHeartbeatMonitor()
        statusCallbackOffline?.invoke()
        scheduleReconnect()
    }

    override fun onMessage(text: String) {
        if (!text.contains("HEARTBEAT_ACK")) {
            Log.d("WS_SERVICE", "Message: $text")
        }
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

    override fun onBinaryMessage(data: ByteArray) {
        val key = sessionKey
        val audioData = if (key != null && com.securecall.crypto.CoreCrypto.isNativeAvailable()) {
            try {
                com.securecall.crypto.CoreCrypto.decrypt(key, data)
            } catch (e: Exception) {
                Log.w("WS_SERVICE", "E2E decrypt failed, dropping frame", e)
                return
            }
        } else {
            data
        }
        if (audioData == null || audioData.isEmpty()) return

        if (!opusDecoderInitialized) {
            com.securecall.app.ghostnet.media.codec.OpusDecoder.init(48000, 1)
            opusDecoderInitialized = true
        }
        val pcm = com.securecall.app.ghostnet.media.codec.OpusDecoder.decode(audioData)
        if (pcm.isNotEmpty()) {
            com.securecall.app.audio.jitter.JitterBuffer.push(pcm)
            startJitterPlayback()
        }
    }

    private fun startJitterPlayback() {
        if (jitterPlaybackRunning) return
        jitterPlaybackRunning = true
        if (audioPlayer == null) {
            audioPlayer = com.securecall.app.ghostnet.media.playback.GhostAudioPlayer(48000, 1)
        }
        val player = audioPlayer ?: return
        val silence = ShortArray(960) // 20ms silence at 48kHz mono
        jitterPlaybackThread = Thread({
            Log.d("WS_SERVICE", "Jitter playout thread started, prefill=${com.securecall.app.audio.jitter.JitterBuffer.PREFILL}")
            // Wait for prefill before starting playback
            while (jitterPlaybackRunning && com.securecall.app.audio.jitter.JitterBuffer.size() < com.securecall.app.audio.jitter.JitterBuffer.PREFILL) {
                try { Thread.sleep(5) } catch (_: InterruptedException) { return@Thread }
            }
            Log.d("WS_SERVICE", "Jitter prefill reached, starting playout")
            while (jitterPlaybackRunning) {
                val frame = com.securecall.app.audio.jitter.JitterBuffer.pop()
                if (frame != null) {
                    player.write(frame)
                } else {
                    player.write(silence)
                }
                try { Thread.sleep(20) } catch (_: InterruptedException) { return@Thread }
            }
            Log.d("WS_SERVICE", "Jitter playout thread stopped")
        }, "jitter-playout")
        jitterPlaybackThread?.start()
    }

    fun stopAudioPlayback() {
        jitterPlaybackRunning = false
        jitterPlaybackThread?.interrupt()
        jitterPlaybackThread = null
        com.securecall.app.audio.jitter.JitterBuffer.clear()
        audioPlayer?.stop()
        audioPlayer?.release()
        audioPlayer = null
        if (opusDecoderInitialized) {
            com.securecall.app.ghostnet.media.codec.OpusDecoder.release()
            opusDecoderInitialized = false
        }
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
        var pubKeyB64 = ""
        if (com.securecall.crypto.CoreCrypto.isNativeAvailable()) {
            val keypair = com.securecall.crypto.CoreCrypto.generateKeyPair()
            localPrivKey = keypair.copyOfRange(0, 32)
            pubKeyB64 = android.util.Base64.encodeToString(keypair.copyOfRange(32, 64), android.util.Base64.NO_WRAP)
            Log.d("WS_SERVICE", "Generated X25519 keypair for outgoing call")
        } else {
            Log.w("WS_SERVICE", "Native crypto unavailable — call will be unencrypted")
        }
        val json = """{"type":"CALL_INVITE","to":"$targetId","pubKey":"$pubKeyB64"}"""
        client?.send(json)
        Log.d("WS_SERVICE", "CALL_INVITE sent to $targetId")
    }

    fun sendCallAccept(sessionId: String) {
        var pubKeyB64 = ""
        val remotePub = remotePubKey
        if (com.securecall.crypto.CoreCrypto.isNativeAvailable() && remotePub != null) {
            val keypair = com.securecall.crypto.CoreCrypto.generateKeyPair()
            localPrivKey = keypair.copyOfRange(0, 32)
            pubKeyB64 = android.util.Base64.encodeToString(keypair.copyOfRange(32, 64), android.util.Base64.NO_WRAP)
            sessionKey = com.securecall.crypto.CoreCrypto.deriveSessionKey(localPrivKey, remotePub)
            Log.d("WS_SERVICE", "E2E session key derived (callee)")
        }
        val json = """{"type":"CALL_ACCEPT","sessionId":"$sessionId","pubKey":"$pubKeyB64"}"""
        client?.send(json)
        Log.d("WS_SERVICE", "CALL_ACCEPT sent for session $sessionId")
        // Callee prepares for WebRTC (will receive offer from caller)
        startWebRtc(sessionId, isOfferer = false)
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

    // ===================== WebRTC P2P =====================

    private fun startWebRtc(sessionId: String, isOfferer: Boolean) {
        Log.d("WS_SERVICE", "Starting WebRTC (offerer=$isOfferer) for session=$sessionId")
        val mgr = WebRtcManager(
            onLocalSdp = { type, sdp -> sendWebRtcSdp(sessionId, type, sdp) },
            onLocalIceCandidate = { candidate -> sendIceCandidate(sessionId, candidate) },
            onDataReceived = { data -> onBinaryMessage(data) },
            onPeerDisconnect = {
                Log.d("WS_SERVICE", "WebRTC peer disconnected — ending call")
                _onCallEnded?.invoke(sessionId)
            }
        )
        webRtcManager = mgr
        mgr.init()
        if (isOfferer) mgr.createOffer()
    }

    private fun sendWebRtcSdp(sessionId: String, type: String, sdp: String) {
        val msgType = if (type == "offer") "WEBRTC_OFFER" else "WEBRTC_ANSWER"
        val obj = org.json.JSONObject()
        obj.put("type", msgType)
        obj.put("sessionId", sessionId)
        obj.put("sdp", sdp)
        client?.send(obj.toString())
        Log.d("WS_SERVICE", "$msgType sent for session=$sessionId")
    }

    private fun sendIceCandidate(sessionId: String, candidate: org.json.JSONObject) {
        val obj = org.json.JSONObject()
        obj.put("type", "ICE_CANDIDATE")
        obj.put("sessionId", sessionId)
        obj.put("candidate", candidate)
        client?.send(obj.toString())
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

        // Phone lookup result
        try {
            val obj = org.json.JSONObject(json)
            if (obj.optString("type") == "PHONE_LOOKUP_RESULT") {
                val clientId = obj.optString("clientId", "")
                    .let { if (it.isEmpty() || it == "null") null else it }
                Log.d("WS_SERVICE", "PHONE_LOOKUP_RESULT: clientId=$clientId")
                _phoneLookupCallback?.invoke(clientId)
                _phoneLookupCallback = null
                return
            }
            if (obj.optString("type") == "BATCH_PHONE_LOOKUP_RESULT") {
                val results = obj.optJSONArray("results")
                val registered = mutableSetOf<String>()
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val r = results.getJSONObject(i)
                        val cId = r.optString("clientId", "")
                        if (cId.isNotEmpty() && cId != "null") {
                            registered.add(r.optString("phoneNumber", ""))
                        }
                    }
                }
                Log.d("WS_SERVICE", "BATCH_PHONE_LOOKUP_RESULT: ${registered.size} registered")
                _batchPhoneLookupCallback?.invoke(registered)
                _batchPhoneLookupCallback = null
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
            when (obj.optString("type")) {
                "CALL_INVITE" -> {
                    val sessionId = obj.optString("sessionId", "")
                    val from = obj.optString("from", "")
                    val pubKeyB64 = obj.optString("pubKey", "")
                    if (pubKeyB64.isNotEmpty()) {
                        remotePubKey = android.util.Base64.decode(pubKeyB64, android.util.Base64.NO_WRAP)
                        Log.d("WS_SERVICE", "Stored caller's X25519 public key")
                    }
                    Log.d("WS_SERVICE", "Incoming CALL_INVITE, sessionId=$sessionId, from=$from")
                    _currentSessionId = sessionId
                    _onIncomingCall?.invoke(sessionId, from)
                }
                "CALL_INVITE_ACK" -> {
                    val ok = obj.optBoolean("ok", false)
                    val sessionId = obj.optString("sessionId", "")
                    Log.d("WS_SERVICE", "CALL_INVITE_ACK ok=$ok, sessionId=$sessionId")
                    if (ok) _currentSessionId = sessionId
                }
                "CALL_ACCEPT" -> {
                    val sessionId = obj.optString("sessionId", "")
                    val pubKeyB64 = obj.optString("pubKey", "")
                    if (pubKeyB64.isNotEmpty() && localPrivKey != null && com.securecall.crypto.CoreCrypto.isNativeAvailable()) {
                        val remotePub = android.util.Base64.decode(pubKeyB64, android.util.Base64.NO_WRAP)
                        sessionKey = com.securecall.crypto.CoreCrypto.deriveSessionKey(localPrivKey, remotePub)
                        Log.d("WS_SERVICE", "E2E session key derived (caller)")
                    }
                    Log.d("WS_SERVICE", "Remote accepted call, sessionId=$sessionId")
                    _onCallAccepted?.invoke(sessionId)
                    // Caller initiates WebRTC P2P
                    startWebRtc(sessionId, isOfferer = true)
                }
                "CALL_ACCEPT_ACK" -> {
                    Log.d("WS_SERVICE", "CALL_ACCEPT_ACK received")
                }
                "CALL_END_ACK" -> {
                    Log.d("WS_SERVICE", "CALL_END_ACK received")
                }
                "WEBRTC_OFFER" -> {
                    val sdp = obj.optString("sdp", "")
                    if (sdp.isNotEmpty()) webRtcManager?.onRemoteOffer(sdp)
                }
                "WEBRTC_ANSWER" -> {
                    val sdp = obj.optString("sdp", "")
                    if (sdp.isNotEmpty()) webRtcManager?.onRemoteAnswer(sdp)
                }
                "WEBRTC_OFFER_ACK", "WEBRTC_ANSWER_ACK", "ICE_CANDIDATE_ACK" -> {
                    // Server acknowledgments — no action needed
                }
                "ICE_CANDIDATE" -> {
                    val candidate = obj.optJSONObject("candidate")
                    if (candidate != null) webRtcManager?.onRemoteIceCandidate(candidate)
                }
                "ERROR" -> {
                    val error = obj.optString("error", "")
                    val message = obj.optString("message", error)
                    Log.e("WS_SERVICE", "Server error: $error — $message")
                    _onCallError?.invoke(error, message)
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleIncomingCallEnd(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            if (obj.optString("type") == "CALL_END") {
                val sessionId = obj.optString("sessionId", "")
                Log.d("WS_SERVICE", "CALL_END received, sessionId=$sessionId")
                _currentSessionId = null
                // Kill all audio immediately — belt-and-suspenders
                killAllAudio()
                // Dismiss IncomingCallActivity if it's showing (caller cancelled during ringing)
                com.securecall.app.IncomingCallActivity.dismissIfActive(sessionId)
                // Also dismiss the incoming call notification directly
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.cancel(INCOMING_CALL_NOTIFICATION_ID)
                _onCallEnded?.invoke(sessionId)
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

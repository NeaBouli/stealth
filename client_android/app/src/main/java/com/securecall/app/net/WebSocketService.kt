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

    // Connection state (volatile for thread-safe reads from UI)
    @Volatile var isConnected: Boolean = false
        private set

    // Call signaling state and callbacks (private backing fields)
    private var _currentSessionId: String? = null
    private var _onIncomingCall: ((String, String, String) -> Unit)? = null
    private var _onCallAccepted: ((String) -> Unit)? = null
    private var _onCallEnded: ((String) -> Unit)? = null
    private var _onCallError: ((String, String) -> Unit)? = null

    // Audio playback pipeline
    private var audioPlayer: com.securecall.app.ghostnet.media.playback.GhostAudioPlayer? = null
    private var opusDecoderInitialized = false
    private var jitterPlaybackThread: Thread? = null
    @Volatile private var jitterPlaybackRunning = false
    @Volatile private var audioPlaybackPaused = false

    // E2E encryption state (X25519 + XChaCha20-Poly1305)
    private var localPrivKey: ByteArray? = null
    private var remotePubKey: ByteArray? = null
    private var sessionKey: ByteArray? = null

    // WebRTC P2P DataChannel transport
    private var webRtcManager: WebRtcManager? = null

    // Phone lookup callback
    private var _phoneLookupCallback: ((String?) -> Unit)? = null
    // Batch phone lookup callback: returns map of hash/phone → online status (true=online, false=offline but registered)
    private var _batchPhoneLookupCallback: ((Map<String, Pair<Boolean, String>>) -> Unit)? = null

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

    // HeartbeatClient owns ALL heartbeat + reconnect logic.
    // WebSocketService does NOT run its own heartbeat monitor.

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

    /** Batch-check which phone hashes are registered SecureCall users. Returns hash → (online, clientId). */
    fun batchPhoneLookup(hashes: List<String>, callback: (registered: Map<String, Pair<Boolean, String>>) -> Unit) {
        _batchPhoneLookupCallback = callback
        val arr = org.json.JSONArray(hashes)
        val json = org.json.JSONObject().apply {
            put("type", "BATCH_PHONE_LOOKUP")
            put("hashes", arr)
        }.toString()
        val sent = client?.send(json) ?: false
        if (!sent) {
            Log.w("WS_SERVICE", "BATCH_PHONE_LOOKUP failed to send")
            _batchPhoneLookupCallback = null
            callback(emptyMap())
            return
        }
        Log.d("WS_SERVICE", "BATCH_PHONE_LOOKUP sent: ${hashes.size} hashes")
    }

    // ===================== HeartbeatClient.Listener =====================

    override fun onConnected() {
        Log.d("WS_SERVICE", "WebSocket connected — registering client")
        isConnected = true
        registerClient()
        setupCallSignalingCallbacks()
        statusCallbackOnline?.invoke()
    }

    private fun setupCallSignalingCallbacks() {
        _onIncomingCall = { sessionId, fromClientId, callerPhone ->
            Log.d("WS_SERVICE", "Incoming call: session=$sessionId, from=$fromClientId, phone=$callerPhone")
            showIncomingCallNotification(sessionId, fromClientId, callerPhone)
        }
    }

    private fun showIncomingCallNotification(sessionId: String, fromClientId: String, callerPhone: String = "") {
        // Resolve caller name: phone book first, then SecureCall contacts, then fallback
        val callerName = com.securecall.app.data.PhoneBookResolver.resolveCallerName(this, fromClientId, callerPhone)

        val intent = android.content.Intent(this, com.securecall.app.IncomingCallActivity::class.java).apply {
            putExtra("sessionId", sessionId)
            putExtra("callerClientId", fromClientId)
            putExtra("callerPhone", callerPhone)
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
            // 1. Primary: user-confirmed number (TelephonyManager is unreliable on many carriers)
            val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
            val confirmedNumber = prefs.getString("confirmed_phone_number", null)
            if (!confirmedNumber.isNullOrBlank()) {
                Log.d("WS_SERVICE", "Device phone number (confirmed): $confirmedNumber")
                return confirmedNumber
            }

            // 2. Legacy fallback: manual_phone_number from old prompt
            val manualNumber = prefs.getString("manual_phone_number", null)
            if (!manualNumber.isNullOrBlank()) {
                Log.d("WS_SERVICE", "Device phone number (manual): $manualNumber")
                return manualNumber
            }

            Log.d("WS_SERVICE", "Device phone number not yet confirmed by user")
            null
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "Failed to read phone number", e)
            null
        }
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected")
        isConnected = false
        statusCallbackOffline?.invoke()
        // HeartbeatClient owns reconnect — do NOT schedule here
    }

    override fun onMessage(text: String) {
        if (!text.contains("HEARTBEAT_ACK")) {
            Log.d("WS_SERVICE", "Message: $text")
        }
        handleIncomingMessageFull(text)
    }

    override fun onError(t: Throwable) {
        Log.e("WS_SERVICE", "WebSocket error", t)
        isConnected = false
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
                if (audioPlaybackPaused) {
                    // During cell call: drain buffer but don't play, write silence
                    com.securecall.app.audio.jitter.JitterBuffer.pop() // discard
                    player.write(silence)
                } else {
                    val frame = com.securecall.app.audio.jitter.JitterBuffer.pop()
                    if (frame != null) {
                        player.write(frame)
                    } else {
                        player.write(silence)
                    }
                }
                try { Thread.sleep(20) } catch (_: InterruptedException) { return@Thread }
            }
            Log.d("WS_SERVICE", "Jitter playout thread stopped")
        }, "jitter-playout")
        jitterPlaybackThread?.start()
    }

    /** Pause audio playback (e.g. during cell call). Playout thread writes silence. */
    fun pauseAudioPlayback() {
        audioPlaybackPaused = true
        Log.d("WS_SERVICE", "Audio playback PAUSED (cell call interruption)")
    }

    /** Resume audio playback after cell call ends. */
    fun resumeAudioPlayback() {
        audioPlaybackPaused = false
        Log.d("WS_SERVICE", "Audio playback RESUMED")
    }

    /** Mark call as active — extends HeartbeatClient staleness threshold. */
    fun setCallActive(active: Boolean) {
        client?.setCallActive(active)
        Log.d("WS_SERVICE", "Call active flag set to $active")
    }

    fun stopAudioPlayback() {
        audioPlaybackPaused = false
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
    // HeartbeatClient owns ALL reconnection logic.
    // WebSocketService only delegates via forceReconnect() or handleHeartbeatTimeout().

    fun forceReconnect() {
        Log.w("WS_SERVICE", "ForceReconnect() invoked — delegating to HeartbeatClient")
        isConnected = false
        statusCallbackOffline?.invoke()
        client?.forceReconnect()
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
        forceReconnect()
        Log.d("WS_SERVICE", "reconnectFlow() completed")
    }

    // HeartbeatClient handles all heartbeat monitoring and timeout detection.
    // No duplicate heartbeat monitor in WebSocketService.

    /**
     * Check if WebSocket is connected. If not, trigger reconnect.
     * Returns true if currently connected, false if reconnecting.
     */
    fun ensureConnected(): Boolean {
        if (isConnected) return true
        Log.w("WS_SERVICE", "ensureConnected() — not connected, triggering reconnect")
        client?.forceReconnect()
        return false
    }

    /**
     * Post-call recovery: force a clean reconnect after any call ends.
     * This ensures the WebSocket is in a clean state for the next call.
     */
    fun postCallRecovery() {
        Log.d("WS_SERVICE", "Post-call recovery — scheduling clean reconnect")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isConnected) {
                Log.w("WS_SERVICE", "Post-call: still disconnected — forcing reconnect")
                client?.forceReconnect()
            } else {
                Log.d("WS_SERVICE", "Post-call: connection is healthy, no recovery needed")
            }
        }, 2000)
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
        // Include caller's phone number so callee can resolve contact name
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        val callerPhone = prefs.getString("confirmed_phone_number", "") ?: ""
        val json = """{"type":"CALL_INVITE","to":"$targetId","pubKey":"$pubKeyB64","callerPhone":"$callerPhone"}"""
        client?.send(json)
        Log.d("WS_SERVICE", "CALL_INVITE sent to $targetId (callerPhone=$callerPhone)")
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
                val mode = obj.optString("mode", "")
                val registered = mutableMapOf<String, Pair<Boolean, String>>()
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val r = results.getJSONObject(i)
                        val cId = r.optString("clientId", "")
                        if (cId.isNotEmpty() && cId != "null") {
                            // Hashed mode returns "hash", legacy returns "phoneNumber"
                            val key = if (mode == "hashed") r.optString("hash", "") else r.optString("phoneNumber", "")
                            val online = r.optBoolean("online", false)
                            if (key.isNotEmpty()) registered[key] = Pair(online, cId)
                        }
                    }
                }
                Log.d("WS_SERVICE", "BATCH_PHONE_LOOKUP_RESULT (mode=$mode): ${registered.size} registered, ${registered.count { it.value.first }} online")
                val cb = _batchPhoneLookupCallback
                _batchPhoneLookupCallback = null
                cb?.invoke(registered)
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
                    val callerPhone = obj.optString("callerPhone", "")
                    val pubKeyB64 = obj.optString("pubKey", "")
                    if (pubKeyB64.isNotEmpty()) {
                        remotePubKey = android.util.Base64.decode(pubKeyB64, android.util.Base64.NO_WRAP)
                        Log.d("WS_SERVICE", "Stored caller's X25519 public key")
                    }
                    Log.d("WS_SERVICE", "Incoming CALL_INVITE, sessionId=$sessionId, from=$from, callerPhone=$callerPhone")
                    _currentSessionId = sessionId
                    _onIncomingCall?.invoke(sessionId, from, callerPhone)
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

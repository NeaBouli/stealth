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

    // BUG-034: Registration gate — true only after REGISTER is processed by server
    @Volatile var isRegistered: Boolean = false
    // Fix CLIENT-CRIT-001 (2026-04-16): track REGISTER failures so we don't
    // burn CPU/battery spinning against a server that permanently rejects us.
    @Volatile private var registerFailCount: Int = 0
    private val maxRegisterFailures = 5
    private var registerTimeoutHandler: android.os.Handler? = null
        private set

    // Guard: prevent double-register on rapid reconnect (network flap)
    @Volatile private var registerPending: Boolean = false

    // BUG-010: FCM-delivered session ID — prevents duplicate IncomingCallActivity on WS reconnect
    @Volatile private var fcmPendingSessionId: String? = null

    // BUG-034: Queued outgoing calls waiting for WS registration
    private val pendingCallQueue = mutableListOf<() -> Unit>()

    // Call signaling state and callbacks (private backing fields)
    // Volatile: callbacks are set on UI thread but read on WebRTC/WS threads
    @Volatile private var _currentSessionId: String? = null
    private var _onIncomingCall: ((String, String, String) -> Unit)? = null
    @Volatile private var _onCallAccepted: ((String) -> Unit)? = null
    @Volatile private var _onCallEnded: ((String) -> Unit)? = null
    @Volatile private var _onCallError: ((String, String) -> Unit)? = null

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
    // Online status callback: returns map of phone → online (true/false)
    private var _onlineStatusCallback: ((Map<String, Boolean>) -> Unit)? = null
    // Activation code callback: returns (success, tier, error)
    private var _activateCodeCallback: ((Boolean, String, String) -> Unit)? = null
    // IFR lock verification callback: returns (success, tier, lockedAmount, error)
    private var _ifrLockCallback: ((Boolean, String, String, String) -> Unit)? = null

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

    /**
     * BUG-010: Called by FCM handler to prevent duplicate IncomingCallActivity.
     * When WS reconnects and receives the same CALL_INVITE, it will be suppressed
     * because we already started the activity from FCM.
     */
    fun setFcmPendingSession(sessionId: String) {
        fcmPendingSessionId = sessionId
        _currentSessionId = sessionId // Mark session active so BUSY isn't sent for same call
        Log.d("WS_SERVICE", "BUG-010: FCM pending session set: $sessionId")
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

    // BUG-009/024: Network change monitor for auto-reconnect
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    // BUG-027: Partial wake lock keeps CPU alive for heartbeats on Samsung A-series
    private var cpuWakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("WS_SERVICE", "onCreate")
        instance = this
        // CRITICAL: Call startForeground() as early as possible.
        // Android 8 (API 26) has a strict 5-second timeout from startForegroundService() to startForeground().
        // On slow devices (e.g. Galaxy S7), any delay here causes an ANR.
        ensureForegroundImmediate()
        createIncomingCallChannel()
        // BUG-027: Acquire partial wake lock to keep heartbeats alive on Samsung devices
        acquireCpuWakeLock()
        // Apply saved network preference before connecting (binds process to preferred network)
        NetworkManager.bindToPreferredNetwork(this)
        client = HeartbeatClient(wsUrl, this)
        client?.connect()
        // BUG-009/024: Register network change callback for instant reconnect
        registerNetworkChangeCallback()
        // BUG-027: Schedule self-restart alarm in case Samsung kills the service
        scheduleServiceRestart()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure foreground notification is up — Android 8 ANR if not called within 5s of startForegroundService()
        startForegroundWithNotification()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("WS_SERVICE", "App swiped away — scheduling restart")
        // BUG-027: Samsung may kill service after swipe — schedule immediate restart
        try {
            val restartIntent = Intent(this, WebSocketService::class.java)
            val pi = android.app.PendingIntent.getService(
                this, 1, restartIntent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            am.set(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 3000, // 3 seconds
                pi
            )
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "Failed to schedule restart after swipe: ${e.message}")
        }
    }

    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy")
        instance = null
        unregisterNetworkChangeCallback()
        releaseCpuWakeLock()
        stopAudioPlayback()
        client?.close()
        super.onDestroy()
    }

    /** BUG-027: Partial wake lock keeps CPU alive for WebSocket heartbeats on aggressive OEMs. */
    private fun acquireCpuWakeLock() {
        if (cpuWakeLock != null) return
        try {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            cpuWakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "securecall:ws_heartbeat")
            cpuWakeLock?.acquire(10 * 60 * 1000L) // 10 min max — prevents indefinite hold if service killed
            Log.d("WS_SERVICE", "CPU wake lock acquired for heartbeats (10min timeout)")
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseCpuWakeLock() {
        cpuWakeLock?.let {
            if (it.isHeld) it.release()
            cpuWakeLock = null
            Log.d("WS_SERVICE", "CPU wake lock released")
        }
    }

    /** BUG-027: Schedule AlarmManager to restart the service if Samsung kills it. */
    private fun scheduleServiceRestart() {
        try {
            val intent = Intent(this, WebSocketService::class.java)
            val pi = android.app.PendingIntent.getService(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            // Repeat every 15 minutes — if service is alive, onStartCommand just returns START_STICKY
            am.setInexactRepeating(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 15 * 60 * 1000,
                15 * 60 * 1000,
                pi
            )
            Log.d("WS_SERVICE", "Service restart alarm scheduled (every 15 min)")
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "Failed to schedule service restart: ${e.message}")
        }
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
                // Fix CLIENT-HIGH-002 (2026-04-16): redact phone — Log.w is not stripped by ProGuard.
                Log.w("WS_SERVICE", "PHONE_LOOKUP timeout (phone redacted)")
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

    /** Request online/offline status for a list of phone numbers. Returns phone → online. */
    fun requestOnlineStatus(phones: List<String>, callback: (statuses: Map<String, Boolean>) -> Unit) {
        _onlineStatusCallback = callback
        val arr = org.json.JSONArray(phones)
        val json = org.json.JSONObject().apply {
            put("type", "ONLINE_STATUS_REQUEST")
            put("phoneNumbers", arr)
        }.toString()
        val sent = client?.send(json) ?: false
        if (!sent) {
            Log.w("WS_SERVICE", "ONLINE_STATUS_REQUEST failed to send")
            _onlineStatusCallback = null
            callback(emptyMap())
            return
        }
        Log.d("WS_SERVICE", "ONLINE_STATUS_REQUEST sent: ${phones.size} phones")
        // Timeout: if no response in 5 seconds, invoke callback with empty map
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val pending = _onlineStatusCallback
            if (pending === callback) {
                Log.w("WS_SERVICE", "ONLINE_STATUS_REQUEST timeout")
                _onlineStatusCallback = null
                callback(emptyMap())
            }
        }, 5000)
    }

    /** Send activation code to server for validation. Returns (success, tier, error). */
    fun activateCode(code: String, callback: (success: Boolean, tier: String, error: String) -> Unit) {
        _activateCodeCallback = callback
        val json = org.json.JSONObject().apply {
            put("type", "ACTIVATE_CODE")
            put("code", code.trim().uppercase())
        }.toString()
        val sent = client?.send(json) ?: false
        if (!sent) {
            Log.w("WS_SERVICE", "ACTIVATE_CODE failed to send")
            _activateCodeCallback = null
            callback(false, "", "not_connected")
            return
        }
        Log.d("WS_SERVICE", "ACTIVATE_CODE sent: ${code.trim().uppercase()}")
        // Timeout: 10 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val pending = _activateCodeCallback
            if (pending === callback) {
                Log.w("WS_SERVICE", "ACTIVATE_CODE timeout")
                _activateCodeCallback = null
                callback(false, "", "timeout")
            }
        }, 10000)
    }

    /** Verify IFR token lock status on Ethereum via server. */
    fun verifyIfrLock(walletAddress: String, callback: (success: Boolean, tier: String, lockedAmount: String, error: String) -> Unit) {
        _ifrLockCallback = callback
        val json = org.json.JSONObject().apply {
            put("type", "VERIFY_IFR_LOCK")
            put("walletAddress", walletAddress.trim())
        }.toString()
        val sent = client?.send(json) ?: false
        if (!sent) {
            _ifrLockCallback = null
            callback(false, "", "0", "not_connected")
            return
        }
        Log.d("WS_SERVICE", "VERIFY_IFR_LOCK sent: $walletAddress")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val pending = _ifrLockCallback
            if (pending === callback) {
                _ifrLockCallback = null
                callback(false, "", "0", "timeout")
            }
        }, 15000)
    }

    // ===================== HeartbeatClient.Listener =====================

    override fun onConnected() {
        // Guard: prevent double-register on rapid reconnect
        if (registerPending) {
            Log.d("WS_SERVICE", "WebSocket connected but register already pending — skipping")
            return
        }
        Log.d("WS_SERVICE", "WebSocket connected — registering client")
        isConnected = true
        isRegistered = false
        registerPending = true
        registerClient()
        setupCallSignalingCallbacks()
        statusCallbackOnline?.invoke()
        com.securecall.app.debug.SecLogManager.logIfEnabled(this, "WS", "Connected")
        // FCM token send + pending-queue flush + IceServer prefetch now happen in
        // onRegisterAck() once the server confirms REGISTER with a REGISTERED
        // message. Previously a hardcoded 1.5s timer could fire even if REGISTER
        // was rejected — which caused REGISTER_FCM_TOKEN to race ahead of REGISTER
        // and the "not_registered" server error seen on S10 in the 2026-04-16 logs.
        scheduleRegisterTimeout()
    }

    // Fix CLIENT-CRIT-001 / MED-002 (2026-04-16): ack-driven registration.
    // We schedule a 5s timeout when we send REGISTER. If the server does not
    // respond with `REGISTERED` in that window, we treat the connection as
    // broken, clear the pending call queue so invites do not fire against an
    // un-registered socket, and force the HeartbeatClient to reconnect.
    private fun scheduleRegisterTimeout() {
        registerTimeoutHandler?.let { it.removeCallbacksAndMessages(null) }
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        registerTimeoutHandler = h
        h.postDelayed({
            if (registerPending && !isRegistered) {
                Log.w("WS_SERVICE", "REGISTER timeout — no REGISTERED ack in 5s, forcing reconnect")
                registerPending = false
                pendingCallQueue.clear()
                try { client?.forceReconnect() } catch (_: Exception) {}
            }
        }, 5000)
    }

    /**
     * Called when the server confirms REGISTER with a {"type":"REGISTERED"} message.
     * This is the only place that flips `isRegistered = true`.
     */
    private fun onRegisterAck(ackedClientId: String) {
        registerTimeoutHandler?.removeCallbacksAndMessages(null)
        registerTimeoutHandler = null
        registerPending = false
        if (!isConnected) return
        isRegistered = true
        registerFailCount = 0
        Log.d("WS_SERVICE", "REGISTERED received for $ackedClientId — flushing ${pendingCallQueue.size} pending calls")
        com.securecall.app.debug.SecLogManager.logIfEnabled(this, "WS", "Registered — ${pendingCallQueue.size} queued calls")
        // Send FCM token now that the server knows who we are.
        com.securecall.app.fcm.FcmTokenManager.ensureTokenRegistered(this)
        IceServerFetcher.prefetch()
        pendingCallQueue.forEach { it.invoke() }
        pendingCallQueue.clear()
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
        // App signature for fork protection — SHA-256 of signing certificate
        val appSignature = getAppSignature()
        val json = org.json.JSONObject().apply {
            put("type", "REGISTER")
            put("clientId", clientId)
            if (phoneNumber != null) put("phoneNumber", phoneNumber)
            put("appSignature", appSignature)
        }.toString()
        client?.send(json)
        Log.d("WS_SERVICE", "REGISTER sent: $clientId, phone: ${phoneNumber ?: "none"}")
    }

    /** Returns SHA-256 hex digest of the app's signing certificate. */
    private fun getAppSignature(): String {
        return try {
            val pm = packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners?.get(0)
                    ?: return "unknown"
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.get(0)
                    ?: return "unknown"
            }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "Failed to get app signature: ${e.message}")
            "unknown"
        }
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
        isRegistered = false // BUG-034: reset registration state
        registerPending = false // Allow re-register on next connect
        statusCallbackOffline?.invoke()
        com.securecall.app.debug.SecLogManager.logIfEnabled(this, "WS", "Disconnected")
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
        isRegistered = false // BUG-034: reset registration state on error
        pendingCallQueue.clear() // BUG-034: clear queued calls — will re-queue on reconnect
        errorCallback?.invoke(t)
        statusCallbackOffline?.invoke()
        com.securecall.app.debug.SecLogManager.logIfEnabled(this, "WS", "Error: ${t.message}")
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

    /** BUG-015: Manual disconnect by user — closes connection without auto-reconnect. */
    fun manualDisconnect() {
        Log.w("WS_SERVICE", "Manual disconnect by user")
        isConnected = false
        statusCallbackOffline?.invoke()
        client?.close()
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
     * BUG-009/024: Register a system-wide network change callback.
     * When the device switches between WiFi, Mobile, or eSIM, trigger an immediate reconnect
     * instead of waiting for the 45s heartbeat timeout.
     */
    @Volatile private var networkWasLost = false

    private fun registerNetworkChangeCallback() {
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                // Only reconnect if network was previously lost (not on initial registration)
                if (networkWasLost) {
                    // BUG-4: Delay reconnect 2s after network switch — DNS may not be ready
                    // immediately (especially on Samsung devices with aggressive power management)
                    Log.d("WS_SERVICE", "NetworkCallback: network available after loss — delaying reconnect 2s for DNS")
                    com.securecall.app.debug.SecLogManager.logIfEnabled(this@WebSocketService, "NET", "Network available — waiting 2s for DNS")
                    networkWasLost = false
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d("WS_SERVICE", "NetworkCallback: DNS delay complete — reconnecting now")
                        com.securecall.app.debug.SecLogManager.logIfEnabled(this@WebSocketService, "NET", "DNS delay done — reconnecting")
                        client?.forceReconnect()
                    }, 2000)
                } else {
                    Log.d("WS_SERVICE", "NetworkCallback: initial network available (no action)")
                }
            }
            override fun onLost(network: android.net.Network) {
                Log.w("WS_SERVICE", "NetworkCallback: network lost")
                com.securecall.app.debug.SecLogManager.logIfEnabled(this@WebSocketService, "NET", "Network lost")
                networkWasLost = true
                isConnected = false
                statusCallbackOffline?.invoke()
            }
        }
        networkCallback = callback
        try {
            cm.registerDefaultNetworkCallback(callback)
            Log.d("WS_SERVICE", "NetworkCallback registered for auto-reconnect")
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "Failed to register NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterNetworkChangeCallback() {
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return
        networkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        networkCallback = null
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
        // BUG-034: If WS is not yet registered after reconnect, queue the call
        if (!isRegistered) {
            Log.w("WS_SERVICE", "BUG-034: WS not registered yet — queuing call to $targetId")
            com.securecall.app.debug.SecLogManager.log("WS", "Call to $targetId queued — waiting for registration")
            pendingCallQueue.add { sendCallInvite(targetId) }
            return
        }

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
        // BUG-032: Mark call active during ringing — prevents heartbeat staleness from killing the WS
        client?.setCallActive(true)
        Log.d("WS_SERVICE", "CALL_INVITE sent to $targetId (callerPhone=$callerPhone, callActive=true)")
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
                cancelCallEndGrace() // BUG-011: cancel any pending server CALL_END grace
                _onCallEnded?.invoke(sessionId)
            }
        )
        webRtcManager = mgr
        // Fetch TURN credentials from backend (removes hardcoded secrets from APK)
        val dynamicIce = IceServerFetcher.fetch()
        mgr.init(dynamicIce)
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

        // Emergency Broadcast — only a template_id, no message content
        try {
            val obj = org.json.JSONObject(json)
            if (obj.optString("type") == "EMERGENCY_BROADCAST") {
                val templateId = obj.optInt("template_id", -1)
                Log.d("WS_SERVICE", "EMERGENCY_BROADCAST received: template_id=$templateId")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    com.securecall.app.emergency.EmergencyBroadcastManager.handleBroadcast(
                        applicationContext, templateId
                    )
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
            if (obj.optString("type") == "IFR_LOCK_RESULT") {
                val success = obj.optBoolean("success", false)
                val tier = obj.optString("tier", "")
                val amount = obj.optString("lockedAmount", "0")
                val error = obj.optString("error", "")
                Log.d("WS_SERVICE", "IFR_LOCK_RESULT: success=$success, tier=$tier, amount=$amount, error=$error")
                val cb = _ifrLockCallback
                _ifrLockCallback = null
                cb?.invoke(success, tier, amount, error)
                return
            }
            if (obj.optString("type") == "ACTIVATE_CODE_RESULT") {
                val success = obj.optBoolean("success", false)
                val tier = obj.optString("tier", "")
                val error = obj.optString("error", "")
                Log.d("WS_SERVICE", "ACTIVATE_CODE_RESULT: success=$success, tier=$tier, error=$error")
                val cb = _activateCodeCallback
                _activateCodeCallback = null
                cb?.invoke(success, tier, error)
                return
            }
            if (obj.optString("type") == "SECUREID_CHANGED") {
                handleSecureIdChanged(obj)
                return
            }
            if (obj.optString("type") == "ONLINE_STATUS_RESPONSE") {
                val statusesObj = obj.optJSONObject("statuses")
                val statuses = mutableMapOf<String, Boolean>()
                if (statusesObj != null) {
                    val keys = statusesObj.keys()
                    while (keys.hasNext()) {
                        val phone = keys.next()
                        statuses[phone] = statusesObj.optBoolean(phone, false)
                    }
                }
                Log.d("WS_SERVICE", "ONLINE_STATUS_RESPONSE: ${statuses.size} phones, ${statuses.count { it.value }} online")
                val cb = _onlineStatusCallback
                _onlineStatusCallback = null
                cb?.invoke(statuses)
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

                    // BUG-010: If FCM already delivered this call, suppress duplicate.
                    // IncomingCallActivity is already ringing — just store the pubKey and session.
                    if (fcmPendingSessionId == sessionId) {
                        Log.d("WS_SERVICE", "BUG-010: CALL_INVITE suppressed (FCM already delivered session $sessionId)")
                        val pubKeyB64 = obj.optString("pubKey", "")
                        if (pubKeyB64.isNotEmpty()) {
                            remotePubKey = android.util.Base64.decode(pubKeyB64, android.util.Base64.NO_WRAP)
                            Log.d("WS_SERVICE", "Stored caller's X25519 public key (from WS after FCM)")
                        }
                        _currentSessionId = sessionId
                        fcmPendingSessionId = null // Clear — WS has taken over
                        return
                    }

                    // FIX 1: Busy signal — reject if already in active call
                    if (_currentSessionId != null) {
                        Log.d("WS_SERVICE", "BUSY: rejecting CALL_INVITE from $from (already in session $_currentSessionId)")
                        val busyJson = """{"type":"CALL_BUSY","sessionId":"$sessionId","from":"${getLocalClientId() ?: ""}"}"""
                        client?.send(busyJson)
                        return
                    }
                    val pubKeyB64 = obj.optString("pubKey", "")
                    if (pubKeyB64.isNotEmpty()) {
                        remotePubKey = android.util.Base64.decode(pubKeyB64, android.util.Base64.NO_WRAP)
                        Log.d("WS_SERVICE", "Stored caller's X25519 public key")
                    }
                    Log.d("WS_SERVICE", "Incoming CALL_INVITE, sessionId=$sessionId, from=$from, callerPhone=$callerPhone")
                    _currentSessionId = sessionId
                    _onIncomingCall?.invoke(sessionId, from, callerPhone)
                }
                "CALL_BUSY" -> {
                    val sessionId = obj.optString("sessionId", "")
                    Log.d("WS_SERVICE", "CALL_BUSY received for session $sessionId")
                    _onCallError?.invoke("busy", "User is busy on another call")
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
                "REGISTERED" -> {
                    val acked = obj.optString("clientId", "")
                    onRegisterAck(acked)
                }
                "ICE_CANDIDATE" -> {
                    val candidate = obj.optJSONObject("candidate")
                    if (candidate != null) webRtcManager?.onRemoteIceCandidate(candidate)
                }
                "ERROR" -> {
                    val error = obj.optString("error", "")
                    val message = obj.optString("message", error)
                    Log.e("WS_SERVICE", "Server error: $error — $message")
                    // Ignore session_not_found from stale WebRTC signaling after call teardown.
                    // These arrive when ICE candidates from a previous call hit the server
                    // after the session was already deleted. Don't trigger _onCallError
                    // because it would crash the next call.
                    if (error == "session_not_found" && _currentSessionId == null) {
                        Log.d("WS_SERVICE", "Ignoring stale session_not_found (no active session)")
                        return
                    }
                    // Fix CLIENT-CRIT-001 (2026-04-16): treat unauthorized_client as a hard
                    // failure. Stop retrying after a handful of attempts so a stuck fork
                    // protection state does not drain the battery with a 4s reconnect loop.
                    if (error == "unauthorized_client") {
                        registerPending = false
                        pendingCallQueue.clear()
                        registerFailCount++
                        if (registerFailCount >= maxRegisterFailures) {
                            Log.e("WS_SERVICE", "REGISTER rejected $registerFailCount× — stopping reconnect loop")
                            try { client?.close() } catch (_: Exception) {}
                        }
                    }
                    _onCallError?.invoke(error, message)
                }
            }
        } catch (_: Exception) {}
    }

    // BUG-011: Delayed CALL_END handler for peer_disconnected during active call
    private var callEndGraceHandler: android.os.Handler? = null
    private var callEndGraceRunnable: Runnable? = null

    /** Cancel any pending CALL_END grace timer (e.g. peer reconnected). */
    fun cancelCallEndGrace() {
        callEndGraceRunnable?.let { callEndGraceHandler?.removeCallbacks(it) }
        callEndGraceHandler = null
        callEndGraceRunnable = null
    }

    private fun handleIncomingCallEnd(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            if (obj.optString("type") == "CALL_END") {
                val sessionId = obj.optString("sessionId", "")
                val reason = obj.optString("reason", "")
                Log.d("WS_SERVICE", "CALL_END received, sessionId=$sessionId, reason=$reason")

                // BUG-011: If the server says "peer_disconnected" but WebRTC ICE is still
                // alive or in grace period, delay the call end by 15s to allow reconnection.
                // The server fires CALL_END when the peer's WebSocket dies (e.g. WiFi toggle),
                // but the P2P audio path may still recover via ICE restart.
                val rtc = webRtcManager
                if (reason == "peer_disconnected" && rtc != null && !rtc.isClosed) {
                    Log.d("WS_SERVICE", "BUG-011: peer_disconnected but WebRTC active — delaying CALL_END 15s")
                    com.securecall.app.debug.SecLogManager.log("CALL", "Delaying CALL_END (peer_disconnected) — waiting 15s for peer reconnect")
                    cancelCallEndGrace()
                    callEndGraceHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    callEndGraceRunnable = Runnable {
                        Log.w("WS_SERVICE", "BUG-011: peer did not reconnect in 15s — ending call now")
                        com.securecall.app.debug.SecLogManager.log("CALL", "Peer reconnect timeout — ending call")
                        executeCallEnd(sessionId)
                    }
                    callEndGraceHandler?.postDelayed(callEndGraceRunnable!!, 15_000)
                    return
                }

                executeCallEnd(sessionId)
            }
        } catch (_: Exception) {}
    }

    private fun executeCallEnd(sessionId: String) {
        cancelCallEndGrace()
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

    /** Handle SECUREID_CHANGED: a device reinstalled and got a new SecureID for the same phone number. */
    private fun handleSecureIdChanged(obj: org.json.JSONObject) {
        val phoneNumber = obj.optString("phoneNumber", "")
        val oldClientId = obj.optString("oldClientId", "")
        val newClientId = obj.optString("newClientId", "")
        Log.d("WS_SERVICE", "SECUREID_CHANGED: phone=$phoneNumber, old=$oldClientId, new=$newClientId")
        if (oldClientId.isEmpty() || newClientId.isEmpty()) return

        val ctx = applicationContext
        // Replace oldClientId with newClientId in local contacts
        val updated = com.securecall.app.data.ContactRepository.replaceSecureId(ctx, oldClientId, newClientId)
        Log.d("WS_SERVICE", "SECUREID_CHANGED: updated $updated contacts (old=$oldClientId -> new=$newClientId)")

        // Invalidate contacts cache so UI refreshes
        com.securecall.app.ui.ContactsFragment.invalidateCache()
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
        Log.w("WS_SERVICE", "updateForegroundMode: enabled=$enabled")
        if (enabled) {
            startForegroundWithNotification()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false // Reset so startForeground() is called again when re-enabled
            // Samsung sometimes keeps the notification after stopForeground — force-cancel it
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(NOTIFICATION_ID)
            Log.w("WS_SERVICE", "stopForeground + cancel notification — background service disabled")
        }
    }

    private var foregroundStarted = false

    /**
     * Minimal startForeground() — called immediately in onCreate() to beat Android 8's 5s timeout.
     * Creates channel + notification inline with no unnecessary work.
     */
    private fun ensureForegroundImmediate() {
        if (foregroundStarted) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Background Service", NotificationManager.IMPORTANCE_LOW).apply {
                            setShowBadge(false)
                        }
                    )
                }
            }
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SecureCall")
                .setContentText("Running in background")
                .setSmallIcon(R.drawable.ic_lock)
                .setOngoing(true)
                .setSilent(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "ensureForegroundImmediate failed", e)
        }
    }

    private fun startForegroundWithNotification() {
        ensureForegroundImmediate()
        // Update notification with proper strings (non-critical, can be deferred)
        try {
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
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "Notification update failed", e)
        }
    }
}

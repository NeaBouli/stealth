package com.securecall.app.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.preference.PreferenceManager
import com.securecall.app.BuildConfig
import com.securecall.app.MainActivity
import com.securecall.app.R
import com.securecall.app.notifications.IncomingCallNotifications
import com.securecall.app.crypto.SessionKeyBinding
import com.securecall.app.security.IdentityProtocol
import com.securecall.app.security.IdentitySigningKey
import com.securecall.app.security.VerifiedCallAccept
import com.securecall.app.security.VerifiedCallInvite

internal object CallEndPolicy {
    fun shouldDelay(reason: String): Boolean = reason == "peer_disconnected"
}

private enum class SecureCallRole { CALLER, CALLEE }

private data class CallSecurityState(
    val role: SecureCallRole,
    val invite: VerifiedCallInvite,
    val inviteTranscript: String,
    var accept: VerifiedCallAccept? = null,
    var acceptTranscript: String? = null,
    var expectedPeerConfirmation: String? = null,
    var localConfirmationSent: Boolean = false,
    var peerConfirmed: Boolean = false,
    var established: Boolean = false,
    var securityCode: String? = null,
)

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
    @Volatile private var _onSecureSessionEstablished: ((String, String) -> Unit)? = null

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
    @Volatile private var callSecurityState: CallSecurityState? = null

    @Volatile private var registrationIdentity: com.securecall.app.security.CallIdentity? = null
    @Volatile private var registrationRequestedClientId: String? = null
    @Volatile private var canonicalRegistrationRetried = false

    // WebRTC P2P DataChannel transport
    private var webRtcManager: WebRtcManager? = null

    // Incoming call ringtone+vibration (played from service so it works even if activity doesn't launch)
    private var incomingRingtone: android.media.MediaPlayer? = null
    private var incomingVibrator: android.os.Vibrator? = null

    // Phone lookup callback
    private var _phoneLookupCallback: ((String?) -> Unit)? = null
    // Legacy batch responses have no request ID, so requests must remain serialized.
    private val batchPhoneLookupTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val batchPhoneLookupQueue = BatchPhoneLookupQueue(
        ::sendBatchPhoneLookup,
        scheduleTimeout = { delayMs, action ->
            batchPhoneLookupTimeoutHandler.postDelayed({ action() }, delayMs)
        },
        onProtocolTimeout = {
            Log.w("WS_SERVICE", "Batch lookup response timeout — reconnecting uncorrelated protocol")
            forceReconnect()
        },
    )
    private val activationRequests = com.securecall.app.billing.ActivationRequestSlot()
    private val activationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val testerLicenses by lazy {
        com.securecall.app.billing.TesterLicenseClient(
            send = { message -> isRegistered && (client?.send(message.toString()) ?: false) },
            deviceIdentity = { createIfMissing ->
                if (createIfMissing) {
                    com.securecall.app.billing.TesterDeviceKey.ensureHardwareKeyIdentity()
                } else {
                    com.securecall.app.billing.TesterDeviceKey.existingHardwareKeyIdentity()
                }
            },
            subject = { getLocalClientId() },
            sign = { com.securecall.app.billing.TesterDeviceKey.signChallenge(it) },
            accept = { com.securecall.app.billing.DirectEntitlementStore(this).accept(it) },
            schedule = { delay, action -> activationHandler.postDelayed({ action() }, delay) }
        )
    }
    private data class PendingEntitlement(val requestId: String, val proof: String)
    @Volatile private var pendingEntitlementProof: PendingEntitlement? = null
    private val entitlementHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val entitlementCheck = object : Runnable {
        override fun run() {
            com.securecall.app.config.TierManager.applyTier(this@WebSocketService)
            refreshDirectEntitlement()
            entitlementHandler.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    private fun refreshDirectEntitlement() {
        if (!isRegistered || pendingEntitlementProof != null) return
        val proof = com.securecall.app.billing.DirectEntitlementStore(this).tokenForRefresh() ?: return
        if (proof.startsWith("sct1.")) {
            if (!com.securecall.app.BuildConfig.TESTER_LICENSE_ENABLED) return
            testerLicenses.start(proof, renewal = true) { _, _ ->
                com.securecall.app.config.TierManager.applyTier(this)
            }
            return
        }
        val pending = PendingEntitlement(java.util.UUID.randomUUID().toString(), proof)
        pendingEntitlementProof = pending
        val sent = client?.send(org.json.JSONObject().apply {
            put("type", "REFRESH_ENTITLEMENT")
            put("requestId", pending.requestId)
            put("entitlementToken", proof)
        }.toString()) ?: false
        if (!sent) pendingEntitlementProof = null
        entitlementHandler.postDelayed({
            if (pendingEntitlementProof == pending) pendingEntitlementProof = null
        }, 10_000)
    }

    fun getCurrentSessionId(): String? = _currentSessionId
    fun setOnCallAccepted(cb: ((String) -> Unit)?) { _onCallAccepted = cb }
    fun setOnCallEnded(cb: ((String) -> Unit)?) { _onCallEnded = cb }
    fun setOnCallError(cb: ((String, String) -> Unit)?) { _onCallError = cb }
    fun setOnSecureSessionEstablished(cb: ((String, String) -> Unit)?) {
        _onSecureSessionEstablished = cb
        val state = callSecurityState
        val code = state?.securityCode
        if (cb != null && state?.established == true && code != null) cb(state.invite.sessionId, code)
    }
    fun isSecureSessionEstablished(): Boolean = callSecurityState?.established == true
    fun getSecurityCode(): String? = callSecurityState?.takeIf { it.established }?.securityCode
    fun clearSession() {
        _currentSessionId = null
        fcmPendingSessionId = null
        localPrivKey?.fill(0)
        localPrivKey = null
        remotePubKey = null
        sessionKey?.fill(0)
        sessionKey = null
        callSecurityState = null
        webRtcManager?.close()
        webRtcManager = null
        killAllAudio()
    }

    /** Stop ALL audio resources globally — ringtone, ringback, playback. Belt-and-suspenders. */
    fun killAllAudio() {
        Log.d("WS_SERVICE", "killAllAudio() — stopping all audio resources")
        try { stopAudioPlayback() } catch (e: Exception) { Log.e("WS_SERVICE", "Error in stopAudioPlayback", e) }
        // Stop service-managed incoming ringtone+vibration
        try { stopIncomingRingtone() } catch (e: Exception) { Log.e("WS_SERVICE", "Error stopping incoming ringtone", e) }
        // Dismiss IncomingCallActivity ringtone+vibration if still active (legacy safety)
        try {
            com.securecall.app.IncomingCallActivity.stopActiveAudio()
        } catch (e: Exception) { Log.e("WS_SERVICE", "Error stopping IncomingCallActivity audio", e) }
        // Stop CallActivity ringback tone if still playing
        try {
            com.securecall.app.CallActivity.stopActiveAudio()
        } catch (e: Exception) { Log.e("WS_SERVICE", "Error stopping CallActivity audio", e) }
    }

    /** Start ringtone+vibration from the service for incoming calls.
     *  Ensures ringing even when IncomingCallActivity can't launch (Android 10+ background restriction). */
    fun startIncomingRingtone() {
        stopIncomingRingtone() // safety: stop any previous
        try {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            incomingRingtone = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@WebSocketService, uri)
                isLooping = true
                prepare()
                start()
            }
            Log.d("WS_SERVICE", "Incoming ringtone started (service)")
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "Failed to start incoming ringtone", e)
        }
        try {
            incomingVibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            val pattern = longArrayOf(0, 1000, 1000)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                incomingVibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                incomingVibrator?.vibrate(pattern, 0)
            }
            Log.d("WS_SERVICE", "Incoming vibration started (service)")
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "Failed to start incoming vibration", e)
        }
    }

    /** Stop incoming ringtone+vibration (called when call is answered, declined, or cancelled). */
    fun stopIncomingRingtone() {
        val player = incomingRingtone
        incomingRingtone = null
        if (player != null) {
            try { player.stop() } catch (_: Exception) {}
            try { player.release() } catch (_: Exception) {}
            Log.d("WS_SERVICE", "Incoming ringtone stopped (service)")
        }
        val vib = incomingVibrator
        incomingVibrator = null
        if (vib != null) {
            try { vib.cancel() } catch (_: Exception) {}
            Log.d("WS_SERVICE", "Incoming vibration stopped (service)")
        }
    }

    fun getLocalClientId(): String? {
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        return prefs.getString("client_id", null)
    }

    fun acceptFcmInvite(payload: String): Boolean {
        val invite = parseVerifiedInvite(payload) ?: return false
        if (!acceptAuthenticatedInvite(invite)) return false
        fcmPendingSessionId = invite.sessionId
        return true
    }

    private fun parseVerifiedInvite(payload: String): VerifiedCallInvite? {
        return try {
            val obj = org.json.JSONObject(payload)
            if (obj.optString("type") !in setOf("CALL_INVITE", "CALL_INVITE_V2")) return null
            val callerPhone = obj.optString("callerPhone", "").takeIf { it.length <= 64 } ?: return null
            val invite = VerifiedCallInvite(
                sessionId = obj.optString("sessionId"),
                fromClientId = obj.optString("from"),
                fromIdentityId = obj.optString("fromIdentityId"),
                to = obj.optString("to"),
                requestedTo = obj.optString("requestedTo"),
                ephemeralPublicKey = obj.optString("ephemeralPublicKey"),
                identityPublicKey = obj.optString("identityPublicKey"),
                issuedAt = obj.optLong("issuedAt", -1),
                nonce = obj.optString("nonce"),
                signature = obj.optString("signature"),
                inviteDigest = obj.optString("inviteDigest"),
                callerPhone = callerPhone,
            )
            val localIdentity = IdentitySigningKey.existingIdentity() ?: return null
            if (invite.to != localIdentity.identityId || getLocalClientId() != localIdentity.identityId) return null
            invite.takeIf {
                IdentityProtocol.verifyCallInvite(it, System.currentTimeMillis() / 1000)
            }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    private fun acceptAuthenticatedInvite(invite: VerifiedCallInvite): Boolean {
        val existing = callSecurityState
        if (existing != null) return existing.invite == invite
        if (_currentSessionId != null && _currentSessionId != invite.sessionId) return false
        val transcript = IdentityProtocol.callInviteTranscript(
            invite.sessionId, invite.fromClientId, invite.fromIdentityId, invite.requestedTo,
            invite.ephemeralPublicKey, invite.issuedAt, invite.nonce,
        ) ?: return false
        val remoteKey = IdentityProtocol.decodeBase64Url(invite.ephemeralPublicKey, 32) ?: return false
        callSecurityState = CallSecurityState(SecureCallRole.CALLEE, invite, transcript)
        remotePubKey = remoteKey
        _currentSessionId = invite.sessionId
        return true
    }

    private fun handleIdentityMigrationChallenge(payload: String) {
        try {
            val obj = org.json.JSONObject(payload)
            if (obj.optString("type") != "IDENTITY_MIGRATION_CHALLENGE") return
            val identity = registrationIdentity ?: IdentitySigningKey.existingIdentity() ?: return
            val challengeId = obj.optString("challengeId")
            val challenge = obj.optString("challenge")
            val requestedClientId = obj.optString("requestedClientId")
            val expiresAt = obj.optLong("expiresAt", -1)
            if (obj.optString("identityId") != identity.identityId
                || !IdentityProtocol.validateRegistrationChallenge(
                    challengeId, challenge, requestedClientId, identity, expiresAt,
                    System.currentTimeMillis() / 1000,
                )) return
            when (com.securecall.app.security.IdentityMigrationChallengePolicy.decide(
                isConnected, registerPending, registrationRequestedClientId, requestedClientId,
            )) {
                com.securecall.app.security.IdentityMigrationChallengePolicy.Action.REJECT -> return
                com.securecall.app.security.IdentityMigrationChallengePolicy.Action.RESTART_REGISTRATION -> {
                    Log.w("WS_SERVICE", "Stale identity migration challenge; requesting a fresh registration")
                    forceReconnect()
                }
                com.securecall.app.security.IdentityMigrationChallengePolicy.Action.SUBMIT -> {
                    if (!submitIdentityProof(challengeId, challenge, requestedClientId, expiresAt)) {
                        Log.w("WS_SERVICE", "Identity migration proof send failed; requesting a fresh registration")
                        forceReconnect()
                    }
                }
            }
        } catch (_: Exception) {
            Log.w("WS_SERVICE", "Invalid identity migration challenge")
        }
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
        private const val PREF_BACKGROUND_SERVICE = "pref_background_service"

        @Volatile
        var instance: WebSocketService? = null
        private const val CHANNEL_ID = "securecall_foreground"
        private const val CHANNEL_INCOMING = "securecall_incoming_call_urgent"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_FCM_CALL_INVITE_V2 = "com.securecall.app.action.FCM_CALL_INVITE_V2"
        const val ACTION_IDENTITY_MIGRATION_CHALLENGE =
            "com.securecall.app.action.IDENTITY_MIGRATION_CHALLENGE"
        const val EXTRA_FCM_PAYLOAD = "fcm_authenticated_call_payload"
        const val EXTRA_IDENTITY_CHALLENGE = "fcm_identity_challenge"
        private const val REGISTER_TIMEOUT_MS = 15_000L

        @JvmStatic
        fun isBackgroundServiceEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_BACKGROUND_SERVICE, true)
        }

        @JvmStatic
        fun hasActiveCall(): Boolean {
            return instance?._currentSessionId != null
        }
    }

    // BUG-009/024: Network change monitor for auto-reconnect
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    // BUG-027: Partial wake lock keeps CPU alive for heartbeats on Samsung devices.
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
        startSignaling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure foreground notification is up — Android 8 ANR if not called within 5s of startForegroundService()
        startForegroundWithNotification()
        if (intent?.action == ACTION_FCM_CALL_INVITE_V2) {
            val payload = intent.getStringExtra(EXTRA_FCM_PAYLOAD).orEmpty()
            val invite = parseVerifiedInvite(payload)
            if (invite != null && acceptAuthenticatedInvite(invite)) {
                fcmPendingSessionId = invite.sessionId
                startIncomingRingtone()
                Log.d("WS_SERVICE", "Authenticated FCM call wake action accepted")
            } else {
                Log.w("WS_SERVICE", "Rejected invalid FCM call wake action")
            }
        } else if (intent?.action == ACTION_IDENTITY_MIGRATION_CHALLENGE) {
            handleIdentityMigrationChallenge(intent.getStringExtra(EXTRA_IDENTITY_CHALLENGE).orEmpty())
        }
        // Ensure the foreground signaling service keeps the CPU alive while it is running.
        acquireCpuWakeLock()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("WS_SERVICE", "App swiped away — scheduling restart")
        // Android 15+ (API 35): never schedule a dataSync restart. Active calls
        // keep the current microphone FGS; idle signaling stops and relies on FCM.
        if (!ForegroundServicePolicy.allowsPersistentIdleSignaling(Build.VERSION.SDK_INT)) {
            if (hasActiveCall()) {
                Log.d("WS_SERVICE", "API 35+ active call — keeping current microphone FGS without restart")
                return
            }
            Log.d("WS_SERVICE", "API 35+ idle task removal — stopping signaling without restart")
            stopSignaling()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (!isBackgroundServiceEnabled(this)) {
            Log.d("WS_SERVICE", "Background service disabled; stopping after task removal")
            stopSignaling()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
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

    /**
     * API 35+: the system calls this when a foreground-service type reaches its
     * time limit (e.g. the ~6h-per-day dataSync budget). While idle we must stop
     * promptly; an active call is kept alive by re-promoting to microphone-only.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("WS_SERVICE", "Foreground service timeout (startId=$startId, fgsType=$fgsType)")
        if (hasActiveCall()) {
            Log.w("WS_SERVICE", "Active call in progress — re-promoting to microphone-only instead of stopping")
            if (updateForegroundServiceType(callActive = true)) return
            Log.e("WS_SERVICE", "Microphone FGS promotion failed during timeout — stopping safely")
        }
        com.securecall.app.debug.SecLogManager.logIfEnabled(
            this,
            "WS",
            "Foreground service timeout — stopping signaling"
        )
        stopSignaling()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy")
        activationHandler.removeCallbacksAndMessages(null)
        runCatching { activationRequests.clear()?.callback?.invoke(false, "", "not_connected") }
        testerLicenses.cancel()
        entitlementHandler.removeCallbacksAndMessages(null)
        pendingEntitlementProof = null
        instance = null
        unregisterNetworkChangeCallback()
        releaseCpuWakeLock()
        stopAudioPlayback()
        client?.close()
        super.onDestroy()
    }

    /** BUG-027: Partial wake lock keeps CPU alive for WebSocket heartbeats on aggressive OEMs. */
    private fun acquireCpuWakeLock() {
        try {
            if (cpuWakeLock == null) {
                val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                cpuWakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "securecall:ws_heartbeat")
                cpuWakeLock?.setReferenceCounted(false)
            }
            if (cpuWakeLock?.isHeld != true) {
                cpuWakeLock?.acquire()
                Log.d("WS_SERVICE", "CPU wake lock acquired for foreground signaling service")
            } else {
                Log.d("WS_SERVICE", "CPU wake lock already held for foreground signaling service")
            }
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

    private fun startSignaling(forceKeepAlive: Boolean = false) {
        acquireCpuWakeLock()
        NetworkManager.bindToPreferredNetwork(this)
        if (client == null) {
            client = HeartbeatClient(wsUrl, this)
            client?.connect()
        } else if (!isConnected) {
            client?.forceReconnect()
        }
        registerNetworkChangeCallback()
        if (forceKeepAlive || isBackgroundServiceEnabled(this)) {
            scheduleServiceRestart()
        } else {
            KeepAliveReceiver.cancel(this)
            Log.d("WS_SERVICE", "Background service disabled; signaling active only for app session")
        }
    }

    private fun stopSignaling() {
        isConnected = false
        isRegistered = false
        statusCallbackOffline?.invoke()
        KeepAliveReceiver.cancel(this)
        unregisterNetworkChangeCallback()
        try { client?.close() } catch (_: Exception) {}
        client = null
        releaseCpuWakeLock()
    }

    /** NEA-180: Schedule Doze-tolerant keep-alive via KeepAliveReceiver.
     *  Also keeps the legacy setInexactRepeating as a belt-and-suspenders fallback. */
    private fun scheduleServiceRestart() {
        // Android 15+ (API 35): restart/keep-alive alarms for the dataSync FGS are not allowed.
        if (!ForegroundServicePolicy.allowsKeepAlive(Build.VERSION.SDK_INT)) {
            KeepAliveReceiver.cancel(this)
            Log.d("WS_SERVICE", "API 35+ — keep-alive alarms not scheduled")
            return
        }
        if (!isBackgroundServiceEnabled(this)) {
            KeepAliveReceiver.cancel(this)
            Log.d("WS_SERVICE", "Background service disabled; keep-alive alarms not scheduled")
            return
        }
        // Primary: Doze-tolerant alarm chain (NEA-180)
        KeepAliveReceiver.scheduleNext(this)

        // Legacy fallback: inexact repeating (fires in normal mode, may be stretched in deep Doze)
        try {
            val intent = Intent(this, WebSocketService::class.java)
            val pi = android.app.PendingIntent.getService(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            am.setInexactRepeating(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 15 * 60 * 1000,
                15 * 60 * 1000,
                pi
            )
            Log.d("WS_SERVICE", "Keep-alive alarms scheduled (idle-tolerant + inexact fallback)")
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "Failed to schedule inexact fallback alarm: ${e.message}")
        }
    }

    // ===================== Public API =====================

    fun sendMessage(text: String) {
        client?.send(text)
    }

    fun sendBinary(data: ByteArray): Boolean {
        // Fail closed: never send plaintext. If crypto unavailable or encryption fails, drop the frame.
        if (callSecurityState?.established != true) return false
        val key = sessionKey ?: return false
        if (!com.securecall.crypto.CoreCrypto.isNativeAvailable()) return false
        val encrypted = com.securecall.crypto.CoreCrypto.encrypt(key, data) ?: return false
        // Send via P2P DataChannel only — no WS relay fallback.
        // During call setup, audio frames before DataChannel opens would
        // flood the signaling WebSocket and trigger server rate limits.
        val rtc = webRtcManager
        if (rtc != null && rtc.isDataChannelOpen) {
            return rtc.send(encrypted)
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
        }, 15_000)
    }

    /** Batch-check which phone hashes are registered SecureCall users. Returns hash → (online, clientId). */
    fun batchPhoneLookup(hashes: List<String>, callback: (registered: Map<String, Pair<Boolean, String>>) -> Unit) {
        batchPhoneLookupQueue.enqueue(hashes, callback)
    }

    private fun sendBatchPhoneLookup(hashes: List<String>): Boolean {
        if (!isConnected || !isRegistered) {
            Log.w("WS_SERVICE", "BATCH_PHONE_LOOKUP rejected before registration")
            return false
        }
        val arr = org.json.JSONArray(hashes)
        val json = org.json.JSONObject().apply {
            put("type", "BATCH_PHONE_LOOKUP")
            put("hashes", arr)
        }.toString()
        val sent = client?.send(json) ?: false
        if (!sent) {
            Log.w("WS_SERVICE", "BATCH_PHONE_LOOKUP failed to send")
            return false
        }
        Log.d("WS_SERVICE", "BATCH_PHONE_LOOKUP sent: ${hashes.size} hashes")
        return true
    }

    internal fun beginActivationRequest(
        callback: (success: Boolean, tier: String, error: String) -> Unit
    ): com.securecall.app.billing.ActivationRequestSlot.Request? = activationRequests.begin(callback)

    internal fun sendActivationMessage(message: String): Boolean = client?.send(message) ?: false

    /** Send activation code to server for validation. Returns (success, tier, error). */
    fun activateCode(code: String, callback: (success: Boolean, tier: String, error: String) -> Unit) {
        val testerCode = code.trim().uppercase().startsWith("SC-PREM-")
        if (!(if (testerCode) com.securecall.app.BuildConfig.TESTER_LICENSE_ENABLED
                else com.securecall.app.BuildConfig.ACTIVATION_CODE_ENABLED)) {
            callback(false, "", "activation_disabled")
            return
        }
        if (testerCode) {
            if (com.securecall.app.BuildConfig.APPLICATION_ID != "com.securecall.app.premium" ||
                com.securecall.app.BuildConfig.TESTER_ENTITLEMENT_PUBLIC_KEY.isEmpty()) {
                callback(false, "", "tester_license_unavailable")
                return
            }
            testerLicenses.start(code.trim().uppercase(), renewal = false) { success, error ->
                com.securecall.app.config.TierManager.applyTier(this)
                callback(success, if (success) "PREMIUM" else "", error)
            }
            return
        }
        val request = beginActivationRequest(callback)
        if (request == null) {
            callback(false, "", "activation_in_progress")
            return
        }
        val json = org.json.JSONObject().apply {
            put("type", "ACTIVATE_CODE")
            put("requestId", request.id)
            put("code", code.trim().uppercase())
        }.toString()
        val sent = sendActivationMessage(json)
        if (!sent) {
            Log.w("WS_SERVICE", "ACTIVATE_CODE failed to send")
            activationRequests.take(request.id)?.callback?.invoke(false, "", "not_connected")
            return
        }
        Log.d("WS_SERVICE", "ACTIVATE_CODE sent")
        // Timeout: 10 seconds
        activationHandler.postDelayed({
            val pending = activationRequests.take(request.id)
            if (pending != null) {
                Log.w("WS_SERVICE", "ACTIVATE_CODE timeout")
                pending.callback(false, "", "timeout")
            }
        }, 10000)
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
    }

    // Fix CLIENT-CRIT-001 / MED-002 (2026-04-16): ack-driven registration.
    // We schedule a bounded timeout when we send the authenticated registration.
    // The window includes an FCM round-trip during one-time legacy-ID migration.
    // respond with `REGISTERED` in that window, we treat the connection as
    // broken, clear the pending call queue so invites do not fire against an
    // un-registered socket, and force the HeartbeatClient to reconnect.
    private fun scheduleRegisterTimeout() {
        registerTimeoutHandler?.let { it.removeCallbacksAndMessages(null) }
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        registerTimeoutHandler = h
        h.postDelayed({
            if (registerPending && !isRegistered) {
                Log.w("WS_SERVICE", "REGISTER timeout — no authenticated ack, forcing reconnect")
                registerPending = false
                pendingCallQueue.clear()
                try { client?.forceReconnect() } catch (_: Exception) {}
            }
        }, REGISTER_TIMEOUT_MS)
    }

    /**
     * Called when the server confirms REGISTER with a {"type":"REGISTERED"} message.
     * This is the only place that flips `isRegistered = true`.
     */
    private fun onRegisterAck(ackedClientId: String, protocol: Int, ackedIdentityId: String) {
        registerTimeoutHandler?.removeCallbacksAndMessages(null)
        registerTimeoutHandler = null
        registerPending = false
        if (!isConnected) return
        val identity = registrationIdentity
        if (protocol != 2 || identity == null || ackedClientId != identity.identityId
            || ackedIdentityId != identity.identityId) {
            Log.e("WS_SERVICE", "Rejected invalid authenticated registration acknowledgement")
            pendingCallQueue.clear()
            try { client?.forceReconnect() } catch (_: Exception) {}
            return
        }
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        val previous = prefs.getString("client_id", null)
        val editor = prefs.edit().putString("client_id", ackedClientId)
        if (!previous.isNullOrEmpty() && previous != ackedClientId) {
            editor.putString("legacy_client_id", previous)
        }
        if (!editor.commit()) {
            Log.e("WS_SERVICE", "Unable to persist authenticated local identity")
            pendingCallQueue.clear()
            try { client?.forceReconnect() } catch (_: Exception) {}
            return
        }
        isRegistered = true
        testerLicenses.cancel()
        pendingEntitlementProof = null
        entitlementHandler.removeCallbacksAndMessages(null)
        entitlementHandler.post(entitlementCheck)
        registerFailCount = 0
        batchPhoneLookupQueue.resume()
        Log.d("WS_SERVICE", "REGISTERED received for $ackedClientId — flushing ${pendingCallQueue.size} pending calls")
        com.securecall.app.debug.SecLogManager.logIfEnabled(this, "WS", "Registered — ${pendingCallQueue.size} queued calls")
        // Send any cached FCM token immediately now that the server knows who
        // we are, then refresh asynchronously for first install/token rotation.
        com.securecall.app.fcm.FcmTokenManager.sendStoredTokenToBackend(this)
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
        if (com.securecall.app.IncomingCallActivity.isAcceptedSession(sessionId)) {
            Log.d("WS_SERVICE", "Suppressing stale IncomingCallActivity launch for accepted session $sessionId")
            return
        }

        // Resolve caller name: phone book first, then SecureCall contacts, then fallback
        val callerName = com.securecall.app.data.PhoneBookResolver.resolveCallerName(this, fromClientId, callerPhone)

        val intent = android.content.Intent(this, com.securecall.app.IncomingCallActivity::class.java).apply {
            putExtra("sessionId", sessionId)
            putExtra("callerClientId", fromClientId)
            putExtra("callerPhone", callerPhone)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // Start ringtone+vibration from service (works even if activity doesn't launch on Android 10+)
        startIncomingRingtone()

        // Direct launch is needed for foreground/background reliability, but FCM
        // and WS can both deliver the same session. Do not relaunch over an
        // already visible incoming UI because it can race the accept transition.
        var directLaunchSucceeded = false
        if (com.securecall.app.IncomingCallActivity.isShowingSession(sessionId)) {
            Log.d("WS_SERVICE", "IncomingCallActivity already visible for session $sessionId — skipping direct relaunch")
            directLaunchSucceeded = true
        } else {
            try {
                startActivity(intent)
                directLaunchSucceeded = true
                Log.d("WS_SERVICE", "IncomingCallActivity launched directly for $callerName")
            } catch (e: Exception) {
                Log.e("WS_SERVICE", "Failed to launch IncomingCallActivity directly", e)
            }
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
            .setFullScreenIntent(fullScreenPending, !directLaunchSucceeded)
            .setContentIntent(fullScreenPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true) // Service handles ringtone — prevent double sound
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(IncomingCallNotifications.WS_NOTIFICATION_ID, notification)
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
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun registerClient() {
        val identity = IdentitySigningKey.ensureIdentity()
        if (identity == null) {
            registerPending = false
            Log.e("WS_SERVICE", "Authenticated identity key unavailable")
            _onCallError?.invoke("identity_key_unavailable", "Secure identity key unavailable")
            return
        }
        registrationIdentity = identity
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        val stored = prefs.getString("client_id", null)
        val requested = if (!canonicalRegistrationRetried
            && stored?.matches(Regex("^[A-Za-z0-9_-]{1,64}$")) == true) stored else identity.identityId
        sendIdentityRegistrationBegin(requested)
    }

    private fun sendIdentityRegistrationBegin(requestedClientId: String) {
        val identity = registrationIdentity ?: return
        registrationRequestedClientId = requestedClientId
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        val json = org.json.JSONObject().apply {
            put("type", "IDENTITY_REGISTER_BEGIN")
            put("protocol", 2)
            put("clientId", requestedClientId)
            put("identityId", identity.identityId)
            put("publicKey", identity.publicKey)
            put("phoneNumber", prefs.getString("confirmed_phone_number", "") ?: "")
            put("appSignature", getAppSignature())
        }.toString()
        if (client?.send(json) != true) {
            registerPending = false
            Log.w("WS_SERVICE", "Authenticated registration request could not be sent")
            return
        }
        scheduleRegisterTimeout()
        Log.d("WS_SERVICE", "Authenticated registration started")
    }

    private fun submitIdentityProof(
        challengeId: String,
        challenge: String,
        requestedClientId: String,
        expiresAt: Long,
    ): Boolean {
        val identity = registrationIdentity ?: IdentitySigningKey.existingIdentity() ?: return false
        if (registrationRequestedClientId != requestedClientId
            || !IdentityProtocol.validateRegistrationChallenge(
                challengeId, challenge, requestedClientId, identity, expiresAt,
                System.currentTimeMillis() / 1000,
            )) return false
        val signature = IdentitySigningKey.sign(challenge) ?: return false
        return client?.send(org.json.JSONObject().apply {
            put("type", "IDENTITY_REGISTER_COMPLETE")
            put("challengeId", challengeId)
            put("signature", signature)
        }.toString()) == true
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
        canonicalRegistrationRetried = false
        registerClient()
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected")
        batchPhoneLookupQueue.suspendAndFailAll()
        isConnected = false
        isRegistered = false // BUG-034: reset registration state
        registerPending = false // Allow re-register on next connect
        registrationRequestedClientId = null
        canonicalRegistrationRetried = false
        statusCallbackOffline?.invoke()
        com.securecall.app.debug.SecLogManager.logIfEnabled(this, "WS", "Disconnected")
        // HeartbeatClient owns reconnect — do NOT schedule here
    }

    override fun onMessage(text: String) {
        if (!text.contains("HEARTBEAT_ACK")) {
            Log.d("WS_SERVICE", "Signaling message received")
        }
        handleIncomingMessageFull(text)
    }

    override fun onError(t: Throwable) {
        Log.e("WS_SERVICE", "WebSocket error", t)
        batchPhoneLookupQueue.suspendAndFailAll()
        isConnected = false
        isRegistered = false // BUG-034: reset registration state on error
        registrationRequestedClientId = null
        canonicalRegistrationRetried = false
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
        if (callSecurityState?.established != true || !com.securecall.crypto.CoreCrypto.isNativeAvailable()) {
            Log.w("WS_SERVICE", "Dropping media before authenticated key confirmation")
            return
        }
        val key = sessionKey ?: return
        val audioData = try {
            com.securecall.crypto.CoreCrypto.decrypt(key, data)
        } catch (e: Exception) {
            Log.w("WS_SERVICE", "E2E decrypt failed, dropping frame", e)
            return
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
        updateForegroundServiceType(callActive = active)
        Log.d("WS_SERVICE", "Call active flag set to $active")
    }

    /**
     * Android 10+ (API 29): keep the foreground-service type explicit.
     * Idle signaling runs as `dataSync`; during an active call the service is
     * promoted to microphone-only (RECORD_AUDIO must be granted) and downgraded
     * back to `dataSync` when the call ends.
     */
    private fun updateForegroundServiceType(callActive: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        if (!foregroundStarted) return false
        val type = if (callActive) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w("WS_SERVICE", "RECORD_AUDIO not granted — cannot promote FGS to microphone type")
                return false
            }
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
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
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            Log.d("WS_SERVICE", "FGS type updated: callActive=$callActive")
            return true
        } catch (e: SecurityException) {
            Log.e("WS_SERVICE", "SecurityException updating FGS type (callActive=$callActive)", e)
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "Failed to update FGS type (callActive=$callActive)", e)
        }
        return false
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

        val identity = IdentitySigningKey.existingIdentity()
        if (identity == null || getLocalClientId() != identity.identityId
            || !com.securecall.crypto.CoreCrypto.isNativeAvailable()) {
            _onCallError?.invoke("secure_session_unavailable", "Authenticated call security is unavailable")
            return
        }
        val keypair = com.securecall.crypto.CoreCrypto.generateKeyPair()
        if (keypair.size != 64) {
            _onCallError?.invoke("secure_session_unavailable", "Authenticated call security is unavailable")
            return
        }
        localPrivKey?.fill(0)
        localPrivKey = keypair.copyOfRange(0, 32)
        val publicKey = IdentityProtocol.encodeBase64Url(keypair.copyOfRange(32, 64))
        keypair.fill(0)
        val sessionId = java.util.UUID.randomUUID().toString()
        val issuedAt = System.currentTimeMillis() / 1000
        val nonce = IdentityProtocol.randomNonce()
        val transcript = IdentityProtocol.callInviteTranscript(
            sessionId, identity.identityId, identity.identityId, targetId,
            publicKey, issuedAt, nonce,
        )
        val signature = transcript?.let(IdentitySigningKey::sign)
        val inviteDigest = transcript?.let(IdentityProtocol::transcriptDigest)
        if (transcript == null || signature == null || inviteDigest == null) {
            clearSession()
            _onCallError?.invoke("invalid_call_target", "The selected SecureCall identity is invalid")
            return
        }
        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        val callerPhone = (prefs.getString("confirmed_phone_number", "") ?: "").take(64)
        val invite = VerifiedCallInvite(
            sessionId, identity.identityId, identity.identityId, targetId, targetId,
            publicKey, identity.publicKey, issuedAt, nonce, signature, inviteDigest, callerPhone,
        )
        callSecurityState = CallSecurityState(SecureCallRole.CALLER, invite, transcript)
        val sent = client?.send(org.json.JSONObject().apply {
            put("type", "CALL_INVITE")
            put("protocol", 2)
            put("sessionId", sessionId)
            put("to", targetId)
            put("ephemeralPublicKey", publicKey)
            put("issuedAt", issuedAt)
            put("nonce", nonce)
            put("signature", signature)
            put("callerPhone", callerPhone)
        }.toString()) == true
        if (!sent) {
            clearSession()
            _onCallError?.invoke("not_connected", "Unable to send authenticated call invitation")
            return
        }
        // BUG-032: Mark call active during ringing — prevents heartbeat staleness from killing the WS
        client?.setCallActive(true)
        Log.d("WS_SERVICE", "Authenticated CALL_INVITE sent")
    }

    fun sendCallAccept(sessionId: String) {
        // Accept is the authoritative local transition out of ringing. Stop both
        // service-owned incoming audio and any activity-owned tones before network
        // signaling so delayed ACKs cannot leave the device audibly ringing.
        killAllAudio()

        if (!isRegistered) {
            Log.w("WS_SERVICE", "CALL_ACCEPT queued — WS not registered for session $sessionId")
            com.securecall.app.debug.SecLogManager.log("WS", "CALL_ACCEPT queued — waiting for registration")
            pendingCallQueue.add { sendCallAccept(sessionId) }
            if (!isConnected) {
                try { client?.forceReconnect() } catch (_: Exception) {}
            }
            return
        }

        val state = callSecurityState
        val identity = IdentitySigningKey.existingIdentity()
        val remotePub = remotePubKey
        if (state?.role != SecureCallRole.CALLEE || state.invite.sessionId != sessionId
            || identity == null || identity.identityId != getLocalClientId() || remotePub == null
            || !com.securecall.crypto.CoreCrypto.isNativeAvailable()) {
            _onCallError?.invoke("invalid_secure_invite", "The authenticated invitation is unavailable")
            return
        }
        val keypair = com.securecall.crypto.CoreCrypto.generateKeyPair()
        if (keypair.size != 64) {
            _onCallError?.invoke("secure_session_unavailable", "Authenticated call security is unavailable")
            return
        }
        localPrivKey?.fill(0)
        localPrivKey = keypair.copyOfRange(0, 32)
        val publicKey = IdentityProtocol.encodeBase64Url(keypair.copyOfRange(32, 64))
        keypair.fill(0)
        val issuedAt = System.currentTimeMillis() / 1000
        val nonce = IdentityProtocol.randomNonce()
        val acceptTranscript = IdentityProtocol.callAcceptTranscript(
            sessionId, state.invite.inviteDigest, identity.identityId, identity.identityId,
            state.invite.fromIdentityId, publicKey, issuedAt, nonce,
        )
        val signature = acceptTranscript?.let(IdentitySigningKey::sign)
        val acceptDigest = acceptTranscript?.let(IdentityProtocol::transcriptDigest)
        val privateKey = localPrivKey
        val baseKey = if (privateKey != null) {
            com.securecall.crypto.CoreCrypto.deriveSessionKey(privateKey, remotePub)
        } else null
        val boundKey = if (baseKey != null && acceptTranscript != null) {
            SessionKeyBinding.bind(baseKey, state.inviteTranscript, acceptTranscript)
        } else null
        baseKey?.fill(0)
        localPrivKey?.fill(0)
        localPrivKey = null
        if (acceptTranscript == null || signature == null || acceptDigest == null || boundKey == null) {
            boundKey?.fill(0)
            _onCallError?.invoke("secure_session_unavailable", "Authenticated key agreement failed")
            return
        }
        sessionKey?.fill(0)
        sessionKey = boundKey
        state.acceptTranscript = acceptTranscript
        state.accept = VerifiedCallAccept(
            sessionId, state.invite.inviteDigest, acceptDigest, identity.identityId,
            identity.identityId, state.invite.fromIdentityId, publicKey,
            identity.publicKey, issuedAt, nonce, signature,
        )
        state.expectedPeerConfirmation = SessionKeyBinding.confirmation(
            boundKey, sessionId, "caller", state.invite.inviteDigest, acceptDigest,
        )
        state.securityCode = SessionKeyBinding.securityCode(
            boundKey, sessionId, state.invite.inviteDigest, acceptDigest,
        )
        val acceptSent = client?.send(org.json.JSONObject().apply {
            put("type", "CALL_ACCEPT")
            put("protocol", 2)
            put("sessionId", sessionId)
            put("inviteDigest", state.invite.inviteDigest)
            put("toIdentityId", state.invite.fromIdentityId)
            put("ephemeralPublicKey", publicKey)
            put("issuedAt", issuedAt)
            put("nonce", nonce)
            put("signature", signature)
        }.toString()) == true
        val localConfirmation = SessionKeyBinding.confirmation(
            boundKey, sessionId, "callee", state.invite.inviteDigest, acceptDigest,
        )
        val confirmationSent = acceptSent && localConfirmation != null && client?.send(
            org.json.JSONObject().apply {
                put("type", "CALL_KEY_CONFIRM")
                put("protocol", 2)
                put("sessionId", sessionId)
                put("role", "callee")
                put("confirmation", localConfirmation)
            }.toString()
        ) == true
        state.localConfirmationSent = confirmationSent
        if (!confirmationSent) {
            _onCallError?.invoke("secure_session_unavailable", "Authenticated key confirmation failed")
            return
        }
        Log.d("WS_SERVICE", "Authenticated CALL_ACCEPT and key confirmation sent")
    }

    @JvmOverloads
    fun sendCallEnd(sessionId: String, reason: String = "user_hangup") {
        val json = """
            {
              "type": "CALL_END",
              "sessionId": "$sessionId",
              "reason": "$reason"
            }
        """.trimIndent()
        client?.send(json)
        Log.d("WS_SERVICE", "CALL_END sent for session $sessionId, reason=$reason")
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
            },
            isExternalVpnActive = { NetworkManager.isExternalVpnActive(this) }
        )
        // BUG-031: if ICE recovers while a CALL_END grace is pending, cancel it
        mgr.onIceRecovered = { cancelCallEndGrace() }
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
                batchPhoneLookupQueue.complete(registered)
                return
            }
            if (testerLicenses.receive(obj)) return
            if (obj.optString("type") == "ENTITLEMENT_REFRESH_RESULT") {
                val pending = pendingEntitlementProof ?: return
                if (obj.optString("requestId") != pending.requestId) return
                pendingEntitlementProof = null
                val store = com.securecall.app.billing.DirectEntitlementStore(this)
                if (store.tokenForRefresh() != pending.proof) return
                if (obj.optBoolean("success", false)) {
                    // A malformed replacement must not destroy the existing proof.
                    // It still expires locally; explicit server revocations clear it.
                    store.accept(obj.optString("entitlementToken", ""))
                } else if (obj.optString("error") in setOf("entitlement_revoked", "invalid_entitlement")) {
                    store.revoke()
                }
                com.securecall.app.config.TierManager.applyTier(this)
                return
            }
            if (obj.optString("type") == "ACTIVATE_CODE_RESULT") {
                val request = activationRequests.take(obj.optString("requestId")) ?: return
                val store = com.securecall.app.billing.DirectEntitlementStore(this)
                val serverSuccess = obj.optBoolean("success", false)
                val success = serverSuccess && store.accept(obj.optString("entitlementToken", ""))
                val tier = if (success) store.currentTier() else ""
                val error = when {
                    success -> ""
                    serverSuccess -> "entitlement_invalid"
                    else -> obj.optString("error", "activation_failed")
                }
                com.securecall.app.config.TierManager.applyTier(this)
                request.callback.invoke(success, tier, error)
                return
            }
            if (obj.optString("type") == "SECUREID_CHANGED") {
                handleSecureIdChanged(obj)
                return
            }
        } catch (_: Throwable) {}

        // Standard signaling
        handleIncomingMessage(json)
        handleIncomingCallEnd(json)
        handleGhostAck(json)
    }

    private fun parseVerifiedAccept(obj: org.json.JSONObject): VerifiedCallAccept? {
        return try {
            val accept = VerifiedCallAccept(
                sessionId = obj.optString("sessionId"),
                inviteDigest = obj.optString("inviteDigest"),
                acceptDigest = obj.optString("acceptDigest"),
                fromClientId = obj.optString("from"),
                fromIdentityId = obj.optString("fromIdentityId"),
                toIdentityId = obj.optString("toIdentityId"),
                ephemeralPublicKey = obj.optString("ephemeralPublicKey"),
                identityPublicKey = obj.optString("identityPublicKey"),
                issuedAt = obj.optLong("issuedAt", -1),
                nonce = obj.optString("nonce"),
                signature = obj.optString("signature"),
            )
            accept.takeIf { IdentityProtocol.verifyCallAccept(it, System.currentTimeMillis() / 1000) }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    private fun handleAuthenticatedAccept(accept: VerifiedCallAccept): Boolean {
        val state = callSecurityState ?: return false
        val identity = IdentitySigningKey.existingIdentity() ?: return false
        if (state.role != SecureCallRole.CALLER || state.invite.sessionId != accept.sessionId
            || state.invite.inviteDigest != accept.inviteDigest
            || accept.toIdentityId != identity.identityId
            || (state.invite.requestedTo.startsWith("sc-")
                && state.invite.requestedTo != accept.fromIdentityId)) return false
        val acceptTranscript = IdentityProtocol.callAcceptTranscript(
            accept.sessionId, accept.inviteDigest, accept.fromClientId, accept.fromIdentityId,
            accept.toIdentityId, accept.ephemeralPublicKey, accept.issuedAt, accept.nonce,
        ) ?: return false
        val remoteKey = IdentityProtocol.decodeBase64Url(accept.ephemeralPublicKey, 32) ?: return false
        val privateKey = localPrivKey ?: return false
        if (!com.securecall.crypto.CoreCrypto.isNativeAvailable()) return false
        val baseKey = com.securecall.crypto.CoreCrypto.deriveSessionKey(privateKey, remoteKey) ?: return false
        val boundKey = SessionKeyBinding.bind(baseKey, state.inviteTranscript, acceptTranscript)
        baseKey.fill(0)
        privateKey.fill(0)
        localPrivKey = null
        if (boundKey == null) return false
        sessionKey?.fill(0)
        sessionKey = boundKey
        remotePubKey = remoteKey
        state.accept = accept
        state.acceptTranscript = acceptTranscript
        state.expectedPeerConfirmation = SessionKeyBinding.confirmation(
            boundKey, accept.sessionId, "callee", accept.inviteDigest, accept.acceptDigest,
        )
        state.securityCode = SessionKeyBinding.securityCode(
            boundKey, accept.sessionId, accept.inviteDigest, accept.acceptDigest,
        )
        val localConfirmation = SessionKeyBinding.confirmation(
            boundKey, accept.sessionId, "caller", accept.inviteDigest, accept.acceptDigest,
        ) ?: return false
        val sent = client?.send(org.json.JSONObject().apply {
            put("type", "CALL_KEY_CONFIRM")
            put("protocol", 2)
            put("sessionId", accept.sessionId)
            put("role", "caller")
            put("confirmation", localConfirmation)
        }.toString()) == true
        state.localConfirmationSent = sent
        return sent
    }

    @Synchronized
    private fun handleKeyConfirmation(obj: org.json.JSONObject): Boolean {
        val state = callSecurityState ?: return false
        if (state.accept == null) return false
        val expectedRole = if (state.role == SecureCallRole.CALLER) "callee" else "caller"
        if (!SessionKeyBinding.acceptsPeerConfirmation(
                established = state.established,
                localConfirmationSent = state.localConfirmationSent,
                protocol = obj.optInt("protocol", -1),
                expectedSessionId = state.invite.sessionId,
                receivedSessionId = obj.optString("sessionId"),
                expectedRole = expectedRole,
                receivedRole = obj.optString("role"),
                expectedConfirmation = state.expectedPeerConfirmation,
                receivedConfirmation = obj.optString("confirmation"),
            )) return false
        val code = state.securityCode ?: return false
        state.peerConfirmed = true
        state.established = true
        _onSecureSessionEstablished?.invoke(state.invite.sessionId, code)
        if (state.role == SecureCallRole.CALLER) {
            killAllAudio()
            _onCallAccepted?.invoke(state.invite.sessionId)
            startWebRtc(state.invite.sessionId, isOfferer = true)
        } else {
            startWebRtc(state.invite.sessionId, isOfferer = false)
        }
        return true
    }

    private fun handleIncomingMessage(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            when (obj.optString("type")) {
                "CALL_INVITE" -> {
                    val invite = parseVerifiedInvite(obj.toString())
                    if (invite == null) {
                        Log.w("WS_SERVICE", "Rejected invalid authenticated CALL_INVITE")
                        return
                    }
                    if (_currentSessionId != null && _currentSessionId != invite.sessionId) {
                        Log.d("WS_SERVICE", "BUSY: rejecting authenticated CALL_INVITE")
                        val busyJson = """{"type":"CALL_BUSY","sessionId":"${invite.sessionId}"}"""
                        client?.send(busyJson)
                        return
                    }
                    if (!acceptAuthenticatedInvite(invite)) {
                        Log.w("WS_SERVICE", "Rejected conflicting authenticated CALL_INVITE")
                        return
                    }
                    if (fcmPendingSessionId == invite.sessionId) {
                        fcmPendingSessionId = null
                        Log.d("WS_SERVICE", "Suppressed duplicate authenticated WS invite after FCM")
                        return
                    }
                    _onIncomingCall?.invoke(invite.sessionId, invite.fromClientId, invite.callerPhone)
                }
                "CALL_BUSY" -> {
                    val sessionId = obj.optString("sessionId", "")
                    Log.d("WS_SERVICE", "CALL_BUSY received for session $sessionId")
                    _onCallError?.invoke("busy", "User is busy on another call")
                }
                "CALL_INVITE_ACK" -> {
                    val ok = obj.optBoolean("ok", false)
                    val sessionId = obj.optString("sessionId", "")
                    val state = callSecurityState
                    if (ok && obj.optInt("protocol", -1) == 2
                        && state?.role == SecureCallRole.CALLER && state.invite.sessionId == sessionId) {
                        _currentSessionId = sessionId
                        Log.d("WS_SERVICE", "Authenticated CALL_INVITE acknowledged")
                    } else {
                        Log.w("WS_SERVICE", "Rejected invalid CALL_INVITE acknowledgement")
                    }
                }
                "CALL_ACCEPT" -> {
                    val accept = parseVerifiedAccept(obj)
                    if (accept == null || !handleAuthenticatedAccept(accept)) {
                        Log.w("WS_SERVICE", "Rejected invalid authenticated CALL_ACCEPT")
                        _onCallError?.invoke("invalid_call_accept", "Authenticated call acceptance failed")
                    }
                }
                "CALL_ACCEPT_ACK" -> {
                    if (obj.optInt("protocol", -1) == 2) {
                        Log.d("WS_SERVICE", "Authenticated CALL_ACCEPT acknowledged")
                        killAllAudio()
                    }
                }
                "CALL_KEY_CONFIRM" -> {
                    if (!handleKeyConfirmation(obj)) {
                        Log.w("WS_SERVICE", "Rejected invalid key confirmation")
                        _onCallError?.invoke("invalid_key_confirmation", "Authenticated key confirmation failed")
                    }
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
                "WEBRTC_OFFER_ACK", "WEBRTC_ANSWER_ACK", "ICE_CANDIDATE_ACK",
                "CALL_KEY_CONFIRM_ACK" -> {
                    // Server acknowledgments — no action needed
                }
                "IDENTITY_REGISTER_CHALLENGE" -> {
                    val requested = registrationRequestedClientId ?: return
                    if (!submitIdentityProof(
                            obj.optString("challengeId"), obj.optString("challenge"),
                            requested, obj.optLong("expiresAt", -1))) {
                        Log.w("WS_SERVICE", "Rejected invalid identity registration challenge")
                    }
                }
                "IDENTITY_MIGRATION_PENDING" -> {
                    Log.d("WS_SERVICE", "Waiting for trusted identity migration challenge")
                }
                "IDENTITY_MIGRATION_REQUIRED" -> {
                    val identity = registrationIdentity ?: return
                    if (canonicalRegistrationRetried
                        || obj.optString("canonicalClientId") != identity.identityId) {
                        Log.w("WS_SERVICE", "Rejected invalid identity migration fallback")
                        return
                    }
                    canonicalRegistrationRetried = true
                    sendIdentityRegistrationBegin(identity.identityId)
                }
                "REGISTERED" -> {
                    val acked = obj.optString("clientId", "")
                    onRegisterAck(acked, obj.optInt("protocol", -1), obj.optString("identityId", ""))
                    // H-01: inject ICE servers from REGISTERED message (avoids public HTTP endpoint)
                    val iceServers = obj.optJSONArray("iceServers")
                    if (iceServers != null && iceServers.length() > 0) {
                        IceServerFetcher.injectFromRegistered(iceServers)
                    }
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
                    if (error == "unauthorized_client" || error.startsWith("identity_")
                        || error == "invalid_identity_proof") {
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

                val rtc = webRtcManager
                // Only the server's explicit disconnect event gets recovery grace.
                // Older servers may omit the reason for a user hangup, so an empty reason
                // must still end the call immediately.
                val isNetworkDisconnect = CallEndPolicy.shouldDelay(reason)
                if (isNetworkDisconnect && rtc != null && !rtc.isClosed) {
                    val logReason = "peer_disconnected"
                    Log.d("WS_SERVICE", "BUG-011: $logReason — delaying CALL_END 15s")
                    com.securecall.app.debug.SecLogManager.log("CALL", "Delaying CALL_END ($logReason) — waiting 15s for peer reconnect")
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

                com.securecall.app.debug.SecLogManager.log("CALL", "CALL_END immediate — reason=$reason")
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
        // Also dismiss incoming call notifications shown by WS or FCM fallback paths.
        IncomingCallNotifications.cancelAll(this)
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

    // Phase 6: SUBSCRIPTION_VERIFY_ACK handling
    private fun handleSubscriptionVerifyAck(obj: org.json.JSONObject) {
        try {
            val success = obj.optBoolean("success", false)
            val requestId = obj.optString("requestId", "")
            val tierStr = obj.getString("tier")
            val expiresAt = obj.optLong("expiresAt", 0L)
            val productId = obj.optString("productId", "")
            val packageName = obj.optString("packageName", "")
            val catalogVersion = obj.optString("catalogVersion", "")
            val tier = com.securecall.app.billing.SubscriptionTier.fromName(tierStr)
            Log.d("WS_SERVICE", "SUBSCRIPTION_VERIFY_ACK: tier=$tierStr, expiresAt=$expiresAt")

            val ctx = applicationContext
            val manager = com.securecall.app.billing.SubscriptionManager(ctx)
            val accepted = success && manager.applyServerVerification(
                requestId = requestId,
                tier = tier,
                expiresAt = expiresAt,
                productId = productId,
                packageName = packageName,
                catalogVersion = catalogVersion
            )
            if (accepted) {
                com.securecall.app.config.TierManager.applyTier(ctx)
            } else {
                manager.clearSubscription()
                com.securecall.app.config.TierManager.applyTier(ctx)
            }
        } catch (t: Throwable) {
            Log.e("WS_SERVICE", "handleSubscriptionVerifyAck() failed", t)
            com.securecall.app.billing.SubscriptionManager(applicationContext).clearSubscription()
            com.securecall.app.config.TierManager.applyTier(applicationContext)
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
            startSignaling(forceKeepAlive = true)
        } else {
            stopSignaling()
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false // Reset so startForeground() is called again when re-enabled
            // Samsung sometimes keeps the notification after stopForeground — force-cancel it
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(NOTIFICATION_ID)
            stopSelf()
            Log.w("WS_SERVICE", "Foreground signaling stopped — background service disabled")
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
            startForegroundAsDataSync(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "ensureForegroundImmediate failed", e)
        }
    }

    /**
     * Starts the foreground service with an explicit `dataSync` type on API 29+
     * so microphone access can be promoted only while a call is active.
     * API 24–28 keep the legacy two-arg
     * startForeground() call (no typed FGS exists there).
     */
    private fun startForegroundAsDataSync(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
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

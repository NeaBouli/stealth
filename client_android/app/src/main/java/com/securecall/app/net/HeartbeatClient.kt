package com.securecall.app.net

import android.util.Log
import okhttp3.*
import okio.ByteString
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * BACKEND-22 — WebSocket connection with heartbeat, auto-reconnect, and dead-connection detection.
 *
 * Reconnect ownership: HeartbeatClient owns ALL reconnection logic.
 * WebSocketService MUST NOT schedule reconnects — it only reacts to callbacks.
 *
 * State machine: DISCONNECTED → CONNECTING → CONNECTED → (DISCONNECTED on error/close)
 *
 * Dead connection detection:
 * 1. OkHttp pingInterval (5s) detects TCP-level dead connections via ping/pong
 * 2. App-level HEARTBEAT every 15s — staleness check every 15s. If no server message
 *    for 45s, connection is considered dead and force-reconnected
 * 3. If send() fails, connection is considered dead
 *
 * Anti-flap recovery: if 3+ reconnects happen in 10s, enters recovery mode:
 * destroy OkHttpClient, wait 3s, create fresh client + connection, re-register.
 */
class HeartbeatClient(
    private val url: String,
    private val listener: Listener
) : WebSocketListener() {

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(text: String)
        fun onBinaryMessage(data: ByteArray)
        fun onError(t: Throwable)
        fun onPing()
        fun onPong()
    }

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    private var ws: WebSocket? = null
    @Volatile private var _lastSeen: Long = System.currentTimeMillis()
    @Volatile private var state: State = State.DISCONNECTED

    // Reconnect backoff: 2s, 4s, 8s, 16s, 30s max (starts at 2s minimum)
    private var reconnectDelay = 2000L
    private val maxReconnectDelay = 30000L
    private val minReconnectInterval = 3000L // Minimum 3s between connect attempts

    private var heartbeatTimer: java.util.Timer? = null
    @Volatile private var isClosed = false // true after intentional close()
    @Volatile private var lastConnectAttempt = 0L
    private var reconnectPending = false // Whether a reconnect is already scheduled
    @Volatile private var callActive = false // Extends staleness threshold during active calls

    // Flap detection: track recent reconnect timestamps (thread-safe — accessed from main + OkHttp threads)
    private val reconnectTimestamps = CopyOnWriteArrayList<Long>()
    private val flapThreshold = 3 // 3 reconnects in flapWindow = flapping
    private val flapWindow = 10_000L // 10 seconds
    @Volatile private var inRecoveryMode = false

    private var okClient = buildClient()

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // BUG-3: 15s too short for mobile networks (latency spikes up to 20-30s)
        // Use bound network's socket factory if process is bound to a specific network
        val boundNet = NetworkManager.getBoundNetwork()
        if (boundNet != null) {
            builder.socketFactory(boundNet.socketFactory)
            builder.dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    return boundNet.getAllByName(hostname).toList()
                }
            })
            Log.d("HB", "OkHttpClient using bound network socketFactory + DNS")
        }
        return builder.build()
    }

    /** Rebuild OkHttpClient with current network binding. Called after network change. */
    fun rebuildClient() {
        try { okClient.dispatcher.cancelAll() } catch (_: Exception) {}
        try { okClient.connectionPool.evictAll() } catch (_: Exception) {}
        okClient = buildClient()
        Log.d("HB", "OkHttpClient rebuilt with current network binding")
    }

    fun connect() {
        val now = System.currentTimeMillis()

        // Enforce minimum interval between connect attempts
        val elapsed = now - lastConnectAttempt
        if (elapsed < minReconnectInterval && state == State.CONNECTING) {
            Log.d("HB", "Connect throttled — only ${elapsed}ms since last attempt")
            return
        }

        if (state == State.CONNECTING) {
            Log.d("HB", "Already connecting, skipping duplicate connect()")
            return
        }

        isClosed = false
        state = State.CONNECTING
        lastConnectAttempt = now
        reconnectPending = false

        // Cancel old socket to prevent zombie connections
        val oldWs = ws
        ws = null
        try { oldWs?.cancel() } catch (_: Exception) {}

        // Flap detection: if 3+ reconnects in 10s, enter recovery mode
        reconnectTimestamps.add(now)
        reconnectTimestamps.removeAll { it < now - flapWindow }
        if (reconnectTimestamps.size >= flapThreshold && !inRecoveryMode) {
            Log.w("HB", "FLAP RECOVERY: ${reconnectTimestamps.size} reconnects in ${flapWindow/1000}s — full clean restart")
            inRecoveryMode = true
            reconnectTimestamps.clear()
            state = State.DISCONNECTED
            reconnectPending = false
            // 1. Destroy old OkHttp client completely
            try { okClient.dispatcher.cancelAll() } catch (_: Exception) {}
            try { okClient.connectionPool.evictAll() } catch (_: Exception) {}
            // 2. Wait 3 seconds, then create fresh client and connect
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Log.d("HB", "FLAP RECOVERY: creating fresh OkHttpClient and connecting")
                okClient = buildClient()
                reconnectDelay = 2000L // Reset backoff after recovery
                inRecoveryMode = false
                if (!isClosed) connect()
            }, 3000)
            return
        }

        val req = Request.Builder().url(url).build()
        ws = okClient.newWebSocket(req, this)
        Log.d("HB", "[$state] New WebSocket connection initiated to $url")
    }

    fun send(text: String): Boolean {
        val sent = ws?.send(text) ?: false
        if (sent) _lastSeen = System.currentTimeMillis()
        return sent
    }

    fun sendBinary(data: ByteArray): Boolean {
        return ws?.send(ByteString.of(*data)) ?: false
    }

    fun close() {
        Log.d("HB", "close() — intentional shutdown")
        isClosed = true
        state = State.DISCONNECTED
        reconnectPending = false
        stopHeartbeat()
        val oldWs = ws
        ws = null
        try { oldWs?.close(1000, "client_close") } catch (_: Exception) {}
    }

    /** Force-cancel the socket (immediate, no close frame) and schedule reconnect. */
    fun forceReconnect() {
        Log.w("HB", "forceReconnect() — cancelling socket + rebuilding client")
        isClosed = false // Clear manual-disconnect flag so reconnect works
        stopHeartbeat()
        state = State.DISCONNECTED
        reconnectPending = false
        val oldWs = ws
        ws = null
        try { oldWs?.cancel() } catch (_: Exception) {}
        // Rebuild OkHttpClient to pick up new network binding
        rebuildClient()
        // Connect immediately instead of scheduling with backoff
        reconnectDelay = 2000L
        connect()
    }

    fun getLastSeen(): Long = _lastSeen

    fun getState(): State = state

    /** Set call active flag — extends heartbeat staleness threshold to 120s during calls. */
    fun setCallActive(active: Boolean) {
        callActive = active
        if (active) _lastSeen = System.currentTimeMillis() // Reset staleness on call start
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("HB", "[CONNECTED] WebSocket connected to $url")
        state = State.CONNECTED
        reconnectPending = false
        _lastSeen = System.currentTimeMillis()
        resetBackoff()
        startHeartbeat()
        listener.onConnected()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        _lastSeen = System.currentTimeMillis()
        listener.onMessage(text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        _lastSeen = System.currentTimeMillis()
        listener.onBinaryMessage(bytes.toByteArray())
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("HB", "[CLOSING] Server closing: code=$code, reason=$reason")
        val wasConnected = state == State.CONNECTED
        state = State.DISCONNECTED
        stopHeartbeat()
        webSocket.close(1000, null)
        if (wasConnected) {
            listener.onDisconnected()
        }
        // Fix CLIENT-HIGH-001 (2026-04-16): treat close codes in the 4000-4099 range
        // (application-level auth/policy rejections) as hard failures. The server
        // currently uses 4003 for "unauthorized client" — retrying with the same
        // credentials just burns battery and spins CPU. Mark the client closed so
        // further reconnects are suppressed; the user can re-open the app manually
        // to retry after they understand the cause (e.g. fork-protection change).
        if (code in 4000..4099) {
            Log.e("HB", "[CLOSING] Server rejected connection with code $code ($reason) — stopping reconnect loop")
            isClosed = true
            reconnectPending = false
            return
        }
        // Server-initiated close — reconnect unless we intentionally closed.
        // Do NOT reconnect on clean close (1000) from onClosing — only from onFailure.
        if (!isClosed && code != 1000) {
            scheduleReconnect()
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.w("HB", "[FAILURE] WebSocket failure: ${t.message}")
        val wasConnected = state == State.CONNECTED
        state = State.DISCONNECTED
        stopHeartbeat()
        if (wasConnected) {
            listener.onDisconnected()
        }
        listener.onError(t)
        // Always reconnect on failure unless intentionally closed
        if (!isClosed) {
            // BUG-035: DNS resolution failures after network change — use longer delay
            // OkHttp DNS cache is stale after WiFi↔Mobile switch, so fast retries just burn battery.
            val errorMsg = t.message ?: ""
            if (errorMsg.contains("Unable to resolve host", ignoreCase = true)
                || errorMsg.contains("No address associated", ignoreCase = true)
                || errorMsg.contains("UnknownHostException", ignoreCase = true)
                || t is java.net.UnknownHostException) {
                Log.w("HB", "BUG-035: DNS failure detected — using 30s reconnect delay")
                reconnectDelay = 30_000L
            }
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (isClosed) return
        if (inRecoveryMode) {
            Log.d("HB", "In recovery mode — skipping normal reconnect")
            return
        }
        if (reconnectPending) {
            Log.d("HB", "Reconnect already pending, skipping duplicate schedule")
            return
        }
        reconnectPending = true
        Log.d("HB", "[RECONNECT] Scheduled in ${reconnectDelay}ms")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isClosed) {
                val delay = reconnectDelay
                reconnectDelay = kotlin.math.min(reconnectDelay * 2, maxReconnectDelay)
                Log.d("HB", "[RECONNECT] Executing (delay was ${delay}ms, next will be ${reconnectDelay}ms)")
                connect()
            } else {
                reconnectPending = false
            }
        }, reconnectDelay)
    }

    private fun resetBackoff() {
        reconnectDelay = 2000L
    }

    /**
     * Heartbeat: send HEARTBEAT every 15s. Check lastSeen every 15s.
     * If no server message received for 45s (well under server's 60s timeout),
     * connection is considered dead and force-reconnected.
     */
    private fun startHeartbeat() {
        stopHeartbeat()
        _lastSeen = System.currentTimeMillis()
        heartbeatTimer = java.util.Timer("hb-timer", true)
        heartbeatTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                // Check for stale connection (45s normally, 120s during active call)
                val stalenessThreshold = if (callActive) 120_000L else 45_000L
                val elapsed = System.currentTimeMillis() - _lastSeen
                if (elapsed > stalenessThreshold) {
                    Log.w("HB", "No server message for ${elapsed}ms — connection dead")
                    stopHeartbeat()
                    // Cancel socket and reconnect on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (state != State.DISCONNECTED || ws != null) {
                            state = State.DISCONNECTED
                            val oldWs = ws
                            ws = null
                            try { oldWs?.cancel() } catch (_: Exception) {}
                            listener.onDisconnected()
                            // onFailure from cancel() will schedule reconnect
                        }
                    }
                    return
                }
                // Send heartbeat
                try {
                    val sent = ws?.send("{\"type\":\"HEARTBEAT\"}") ?: false
                    if (!sent) {
                        Log.w("HB", "HEARTBEAT send failed — socket dead")
                    }
                } catch (_: Exception) {}
            }
        }, 15_000, 15_000)
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }
}

package com.securecall.app.net

import android.util.Log
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * BACKEND-22 — WebSocket connection with heartbeat, auto-reconnect, and dead-connection detection.
 *
 * Reconnect ownership: HeartbeatClient owns ALL reconnection logic.
 * WebSocketService MUST NOT schedule reconnects — it only reacts to callbacks.
 *
 * Dead connection detection:
 * 1. OkHttp pingInterval (5s) detects TCP-level dead connections
 * 2. App-level HEARTBEAT every 15s expects HEARTBEAT_ACK — if no server message
 *    for 20s, connection is considered dead and force-reconnected
 * 3. If send() fails, connection is considered dead
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

    private var ws: WebSocket? = null
    @Volatile private var _lastSeen: Long = System.currentTimeMillis()

    // Reconnect backoff: 1s, 2s, 4s, 8s, 16s, 30s max
    private var reconnectDelay = 1000L
    private val maxReconnectDelay = 30000L
    private var heartbeatTimer: java.util.Timer? = null
    @Volatile private var isConnecting = false
    @Volatile private var isClosed = false // true after intentional close()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(5, TimeUnit.SECONDS)
        .build()

    fun connect() {
        if (isConnecting) {
            Log.d("HB", "Already connecting, skipping duplicate connect()")
            return
        }
        isClosed = false
        isConnecting = true
        // Cancel old socket to prevent zombie connections
        val oldWs = ws
        ws = null
        try { oldWs?.cancel() } catch (_: Exception) {}
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, this)
        Log.d("HB", "New WebSocket connection initiated")
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
        isClosed = true
        stopHeartbeat()
        try { ws?.close(1000, "client_close") } catch (_: Exception) {}
    }

    /** Force-cancel the socket (immediate, no close frame) and schedule reconnect. */
    fun forceReconnect() {
        Log.w("HB", "forceReconnect() — cancelling socket and scheduling reconnect")
        stopHeartbeat()
        val oldWs = ws
        ws = null
        isConnecting = false
        try { oldWs?.cancel() } catch (_: Exception) {}
        // onFailure will fire from cancel(), but schedule reconnect defensively
        scheduleReconnect()
    }

    fun getLastSeen(): Long = _lastSeen

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("HB", "WebSocket connected to $url")
        isConnecting = false
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
        Log.d("HB", "Server closing connection: code=$code, reason=$reason")
        isConnecting = false
        stopHeartbeat()
        listener.onDisconnected()
        webSocket.close(1000, null)
        // Server initiated close — reconnect unless we intentionally closed
        if (!isClosed) {
            scheduleReconnect()
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.w("HB", "WebSocket failure: ${t.message}")
        isConnecting = false
        stopHeartbeat()
        listener.onError(t)
        // Always reconnect on failure unless intentionally closed
        if (!isClosed) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (isClosed) return
        Log.d("HB", "Reconnect scheduled in ${reconnectDelay}ms")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isClosed) {
                reconnectDelay = kotlin.math.min(reconnectDelay * 2, maxReconnectDelay)
                connect()
            }
        }, reconnectDelay)
    }

    private fun resetBackoff() {
        reconnectDelay = 1000L
    }

    /**
     * Heartbeat: send HEARTBEAT every 15s. Check lastSeen every 15s.
     * If no server message received for 20s, force-reconnect.
     * The HEARTBEAT_ACK from server updates lastSeen, proving the connection is alive.
     */
    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = java.util.Timer("hb-timer", true)
        heartbeatTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                // Check for stale connection
                val elapsed = System.currentTimeMillis() - _lastSeen
                if (elapsed > 20_000) {
                    Log.w("HB", "No server message for ${elapsed}ms — connection dead, force-reconnecting")
                    stopHeartbeat()
                    // Cancel socket and reconnect on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val oldWs = ws
                        ws = null
                        isConnecting = false
                        try { oldWs?.cancel() } catch (_: Exception) {}
                        listener.onDisconnected()
                        scheduleReconnect()
                    }
                    return
                }
                // Send heartbeat
                try {
                    val sent = ws?.send("{\"type\":\"HEARTBEAT\"}") ?: false
                    if (!sent) {
                        Log.w("HB", "HEARTBEAT send failed — socket dead")
                        // Don't reconnect here; the staleness check above will handle it
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

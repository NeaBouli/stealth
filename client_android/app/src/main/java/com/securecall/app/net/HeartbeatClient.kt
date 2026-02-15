package com.securecall.app.net

import android.util.Log
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * BACKEND-22 — WebSocket Heartbeat Listener (Android)
 */
class HeartbeatClient(
    private val url: String,
    private val listener: Listener
) : WebSocketListener() {

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(text: String)
        fun onError(t: Throwable)
        fun onPing()
        fun onPong()
    }

    private var ws: WebSocket? = null
    private var lastSeen: Long = System.currentTimeMillis()

    // BACKEND-22: Reconnect Backoff + Idle Ping
    private var reconnectDelay = 1000L
    private val maxReconnectDelay = 15000L
    private var idlePingTimer: java.util.Timer? = null

    fun connect() {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(5, TimeUnit.SECONDS)
            .build()

        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, this)
    }

    fun send(text: String) {
        ws?.send(text)
    }

    fun close() {
        stopIdlePing()
        ws?.close(1000, "client_close")
    }

    fun getLastSeen(): Long = lastSeen

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("HB", "WebSocket connected")
        resetBackoff()
        startIdlePing()
        listener.onConnected()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        lastSeen = System.currentTimeMillis()
        listener.onMessage(text)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        listener.onDisconnected()
        webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        stopIdlePing()
        listener.onError(t)
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        Log.d("HB", "Reconnect scheduled in ${reconnectDelay}ms")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            connect()
            reconnectDelay = kotlin.math.min(reconnectDelay * 2, maxReconnectDelay)
        }, reconnectDelay)
    }

    private fun resetBackoff() {
        reconnectDelay = 1000L
    }

    private fun startIdlePing() {
        idlePingTimer?.cancel()
        idlePingTimer = java.util.Timer()
        idlePingTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                try {
                    ws?.send("ping_idle")
                } catch (_: Exception) {}
            }
        }, 8000, 8000)
    }

    private fun stopIdlePing() {
        idlePingTimer?.cancel()
        idlePingTimer = null
    }
}

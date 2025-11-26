package com.securecall.app.net

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

/**
 * BACKEND-22 — WebSocketService
 *
 * Haelt die WebSocket-Verbindung zum Signaling-Server im Hintergrund
 * und kapselt den HeartbeatClient.
 */

class WebSocketService : Service(), HeartbeatClient.Listener {

    private val binder = LocalBinder()
    private var client: HeartbeatClient? = null

    // TODO: spaeter konfigurierbar machen (Settings / BuildConfig)
    private val wsUrl: String = "ws://10.0.2.2:8080/signal"

    inner class LocalBinder : Binder() {
        fun getService(): WebSocketService = this@WebSocketService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WS_SERVICE", "onCreate")
        client = HeartbeatClient(wsUrl, this)
        client?.connect()
    }

    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy")
        client?.close()
        super.onDestroy()
    }

    // Public API fuer Activities / Fragments

    fun sendMessage(text: String) {
        client?.send(text)
    }

    fun lastSeen(): Long {
        return client?.getLastSeen() ?: 0L
    }

    // HeartbeatClient.Listener Implementierung

    override fun onConnected() {
        Log.d("WS_SERVICE", "WebSocket connected")
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected")
    }

    override fun onMessage(text: String) {
        Log.d("WS_SERVICE", "Message: $text")
        // TODO: spaeter Broadcast / LiveData / EventBus
    }

    override fun onError(t: Throwable) {
        Log.e("WS_SERVICE", "WebSocket error", t)
    }

    override fun onPing() {
        Log.d("WS_SERVICE", "Ping received")
    }

    override fun onPong() {
        Log.d("WS_SERVICE", "Pong received")
    }
}

    // BACKEND-22: Status-Callback für MainActivity
    var statusCallbackOnline: (() -> Unit)? = null
    var statusCallbackOffline: (() -> Unit)? = null

    override fun onConnected() {
        Log.d("WS_SERVICE", "WebSocket connected")
        statusCallbackOnline?.invoke()
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected")
        statusCallbackOffline?.invoke()
    }

    // BACKEND-22: Auto-Reconnect & Fehler-Callback
    var errorCallback: ((Throwable) -> Unit)? = null

    private fun scheduleReconnect() {
        android.os.Handler(mainLooper).postDelayed({
            Log.d("WS_SERVICE", "Reconnecting WebSocket...")
            client?.connect()
        }, 3000) // 3 Sekunden warten
    }

    override fun onError(t: Throwable) {
        Log.e("WS_SERVICE", "WebSocket error", t)
        errorCallback?.invoke(t)
        scheduleReconnect()
    }

    // BACKEND-22: Auto-Reconnect & Fehler-Callback
    var errorCallback: ((Throwable) -> Unit)? = null

    private fun scheduleReconnect() {
        android.os.Handler(mainLooper).postDelayed({
            Log.d("WS_SERVICE", "Reconnecting WebSocket...")
            client?.connect()
        }, 3000) // 3 Sekunden warten
    }

    override fun onError(t: Throwable) {
        Log.e("WS_SERVICE", "WebSocket error", t)
        errorCallback?.invoke(t)
        scheduleReconnect()
    }

    // BACKEND-22: Idle-Ping beim Stoppen abschalten
    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy: stopping idle ping")
        client?.close()
        super.onDestroy()
    }

    // BACKEND-22: CALL_INVITE senden
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

    // BACKEND-22: CALL_ACCEPT senden
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

    // BACKEND-22: Eingehendes Signaling verarbeiten
    private fun handleIncomingMessage(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            when (obj.getString("type")) {

                "CALL_INVITE" -> {
                    val sessionId = obj.optString("sessionId", "")
                    com.securecall.app.session.SessionManager.setSession(sessionId)
                    Log.d("WS_SERVICE", "Incoming CALL_INVITE, sessionId=$sessionId")
                }

                "CALL_ACCEPT" -> {
                    Log.d("WS_SERVICE", "Remote accepted call")
                }
            }
        } catch (_: Exception) {}
    }

    // Hook in onMessage
    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("WS_SERVICE", "Message received: $text")
        handleIncomingMessage(text)
        listener.onMessage(text)
    }

    // BACKEND-22: CALL_END senden
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

    // BACKEND-22: Session Reset wenn Remote CALL_END schickt
    private fun handleIncomingCallEnd(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            if (obj.getString("type") == "CALL_END") {
                com.securecall.app.session.SessionManager.clear()
                Log.d("WS_SERVICE", "CALL_END received from remote")
            }
        } catch (_: Exception) {}
    }

    // Hook in existing handleIncomingMessage
    private fun handleIncomingMessageEx(json: String) {
        handleIncomingMessage(json)
        handleIncomingCallEnd(json)
    }


    // BACKEND-22: erweitertes Message-Handling
    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("WS_SERVICE", "Message received: $text")
        handleIncomingMessageEx(text)
        listener.onMessage(text)
    }

    // BACKEND-23: GHOST_PREPARE senden
    fun sendGhostPrepare(sessionId: String) {
        val keyMaterial = com.securecall.app.crypto.EphemeralKeyProvider.generateKeyMaterial()

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

    // BACKEND-24: GHOST_ACK handling
    private fun handleGhostAck(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            if (obj.getString("type") == "GHOST_ACK") {

                val id = obj.getString("ghostNetId")
                val relayArray = obj.getJSONArray("relayHints")

                val hints = mutableListOf<com.securecall.app.ghostnet.GhostNetRelayHint>()
                for (i in 0 until relayArray.length()) {
                    val r = relayArray.getJSONObject(i)
                    hints.add(
                        com.securecall.app.ghostnet.GhostNetRelayHint(
                            host = r.getString("host"),
                            port = r.getInt("port")
                        )
                    )
                }

                com.securecall.app.ghostnet.GhostNetSession.setSession(id, hints)

                Log.d("WS_SERVICE", "GHOST_ACK received → ghostNetId=$id")
            }
        } catch (_: Exception) {}
    }

    // BACKEND-24: erweitertes Message-Dispatch
    private fun handleIncomingMessageEx2(json: String) {
        handleIncomingMessageEx(json)
        handleGhostAck(json)
    }

    // override onMessage erneut erweitern
    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("WS_SERVICE", "Message received: $text")
        handleIncomingMessageEx2(text)
        listener.onMessage(text)
    }

    // BACKEND-24: GHOST_ACK handling
    private fun handleGhostAck(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            if (obj.getString("type") == "GHOST_ACK") {

                val id = obj.getString("ghostNetId")
                val relayArray = obj.getJSONArray("relayHints")

                val hints = mutableListOf<com.securecall.app.ghostnet.GhostNetRelayHint>()
                for (i in 0 until relayArray.length()) {
                    val r = relayArray.getJSONObject(i)
                    hints.add(
                        com.securecall.app.ghostnet.GhostNetRelayHint(
                            host = r.getString("host"),
                            port = r.getInt("port")
                        )
                    )
                }

                com.securecall.app.ghostnet.GhostNetSession.setSession(id, hints)

                Log.d("WS_SERVICE", "GHOST_ACK received → ghostNetId=$id")
            }
        } catch (_: Exception) {}
    }

    // BACKEND-24: erweitertes Message-Dispatch
    private fun handleIncomingMessageEx2(json: String) {
        handleIncomingMessageEx(json)
        handleGhostAck(json)
    }

    // override onMessage erneut erweitern
    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("WS_SERVICE", "Message received: $text")
        handleIncomingMessageEx2(text)
        listener.onMessage(text)
    }

    // BACKEND-25: Reset GhostNetSession bei Disconnect
    private fun resetGhostSession() {
        com.securecall.app.ghostnet.GhostNetSession.resetSession()
        android.util.Log.d("WS_SERVICE", "GhostNetSession reset due to WS disconnect")
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected")
        resetGhostSession()
        statusCallbackOffline?.invoke()
    }

    // BACKEND-25: Heartbeat Überwachung
    private val heartbeatIntervalMs = 5000L
    private val heartbeatTimeoutMs = 15000L
    private var heartbeatTimer: java.util.Timer? = null

    private fun startHeartbeatMonitor() {
        heartbeatTimer?.cancel()
        heartbeatTimer = java.util.Timer()
        heartbeatTimer?.schedule(object : java.util.TimerTask() {
            override fun run() {
                val last = heartbeatClient?.getLastSeen() ?: 0
                val now = System.currentTimeMillis()
                if (now - last > heartbeatTimeoutMs) {
                    android.util.Log.w("WS_SERVICE", "Heartbeat timeout → treating as disconnect")
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
        // Session hart zurücksetzen
        com.securecall.app.ghostnet.GhostNetSession.resetSession()

        // UI / Status informieren
        statusCallbackOffline?.invoke()

        // WebSocket schließen
        try {
            webSocket?.close(1001, "Heartbeat timeout")
        } catch (_: Exception) { }
    }

    override fun onConnected() {
        Log.d("WS_SERVICE", "WebSocket connected (HB active)")
        startHeartbeatMonitor()
        statusCallbackOnline?.invoke()
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected (HB stop)")
        stopHeartbeatMonitor()
        com.securecall.app.ghostnet.GhostNetSession.resetSession()
        statusCallbackOffline?.invoke()
    }

    // BACKEND-26: Reconnect-Backoff
    private var reconnectAttempts = 0
    private val backoffSequenceMs = longArrayOf(1000L, 3000L, 5000L)

    private fun scheduleReconnect() {
        val delay = backoffSequenceMs[
            if (reconnectAttempts >= backoffSequenceMs.size) backoffSequenceMs.size - 1
            else reconnectAttempts
        ]

        android.util.Log.w("WS_SERVICE", "Scheduling reconnect in ${delay}ms (attempt $reconnectAttempts)")

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            reconnectAttempts++
            connectWebSocket()
        }, delay)
    }

    override fun onConnected() {
        Log.d("WS_SERVICE", "WebSocket connected — resetting backoff")
        reconnectAttempts = 0
        startHeartbeatMonitor()
        statusCallbackOnline?.invoke()
    }

    override fun onDisconnected() {
        Log.d("WS_SERVICE", "WebSocket disconnected — starting backoff reconnect")
        stopHeartbeatMonitor()
        com.securecall.app.ghostnet.GhostNetSession.resetSession()
        statusCallbackOffline?.invoke()
        scheduleReconnect()
    }

    override fun onError(t: Throwable) {
        Log.e("WS_SERVICE", "WebSocket error: ${t.message}")
        statusCallbackOffline?.invoke()
        scheduleReconnect()
    }

    // BACKEND-26: Startschuss für Reconnect-Loop
    private fun connectWebSocket() {
        // bestehender Code unverändert…
        // **diese Zeile ans Ende hängen**:
        Log.d("WS_SERVICE", "connectWebSocket() invoked")
    }

    // BACKEND-26: Manuelles Forcieren eines WS-Reconnects
    fun forceReconnect() {
        android.util.Log.w("WS_SERVICE", "ForceReconnect() invoked — closing WS + scheduling reconnect")
        try {
            webSocket?.close(1000, "forced reconnect")
        } catch (_: Exception) { }

        stopHeartbeatMonitor()
        com.securecall.app.ghostnet.GhostNetSession.resetSession()
        statusCallbackOffline?.invoke()
        scheduleReconnect()
    }

    // BACKEND-31: State Listener für Service (optional Hook)
    private val ghostStateListener =
        com.securecall.app.ghostnet.GhostNetSession.StateListener { state ->
            android.util.Log.d("WS_SERVICE", "GhostNet state updated: $state")
        }

    override fun onCreate() {
        super.onCreate()
        com.securecall.app.ghostnet.GhostNetSession.addStateListener(ghostStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        com.securecall.app.ghostnet.GhostNetSession.removeStateListener(ghostStateListener)
    }

    // BACKEND-32: Lifecycle Listener (Service)
    private val ghostLifecycleListener =
        object : com.securecall.app.ghostnet.GhostNetSession.LifecycleListener {
            override fun onPrepared() {
                android.util.Log.d("WS_SERVICE", "Lifecycle → PREPARED")
            }

            override fun onActivated() {
                android.util.Log.d("WS_SERVICE", "Lifecycle → ACTIVE")
            }

            override fun onDead() {
                android.util.Log.d("WS_SERVICE", "Lifecycle → DEAD")
            }
        }

    override fun onCreate() {
        super.onCreate()
        com.securecall.app.ghostnet.GhostNetSession.addLifecycleListener(ghostLifecycleListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        com.securecall.app.ghostnet.GhostNetSession.removeLifecycleListener(ghostLifecycleListener)
    }

    // BACKEND-33: Transport-Steuerung über Lifecycle
    private val transportLifecycleListener =
        object : com.securecall.app.ghostnet.GhostNetSession.LifecycleListener {
            override fun onPrepared() {
                // Noch kein Transport – nur Pre-Handshake
            }

            override fun onActivated() {
                com.securecall.app.ghostnet.transport.GhostTransport.get().startTransport()
            }

            override fun onDead() {
                com.securecall.app.ghostnet.transport.GhostTransport.get().stopTransport()
            }
        }

    override fun onCreate() {
        super.onCreate()
        com.securecall.app.ghostnet.GhostNetSession.addLifecycleListener(transportLifecycleListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        com.securecall.app.ghostnet.GhostNetSession.removeLifecycleListener(transportLifecycleListener)
    }

    // BACKEND-41: Session Lifecycle Integration
    private fun updateSessionActive() {
        com.securecall.app.ghostnet.session.GhostNetSession.get()
            .setState(com.securecall.app.ghostnet.session.GhostNetSessionState.ACTIVE)
    }

    private fun updateSessionDead() {
        com.securecall.app.ghostnet.session.GhostNetSession.get()
            .setState(com.securecall.app.ghostnet.session.GhostNetSessionState.DEAD)
    }

    override fun onConnected() {
        super.onConnected()
        updateSessionActive()
    }

    override fun onDisconnected() {
        super.onDisconnected()
        updateSessionDead()
    }

    // BACKEND-56: WS-Reconnect-Scheduling
    private fun scheduleReconnectHook(reason: String) {
        com.securecall.app.net.reconnect.WebSocketReconnect.scheduleReconnect(reason)
    }

    override fun onDisconnected() {
        android.util.Log.d("WS_SERVICE", "WebSocket disconnected")
        statusCallbackOffline?.invoke()

        // Hook aktivieren
        scheduleReconnectHook("onDisconnected")
    }

    override fun onError(t: Throwable) {
        android.util.Log.e("WS_SERVICE", "WebSocket error", t)
        statusCallbackOffline?.invoke()

        // Hook aktivieren
        scheduleReconnectHook("onError")
    }

    // BACKEND-56: aktiven Reconnect abbrechen bei erfolgreicher Verbindung
    private fun cancelReconnectHook() {
        com.securecall.app.net.reconnect.WebSocketReconnect.cancel()
    }

    override fun onConnected() {
        android.util.Log.d("WS_SERVICE", "WebSocket connected")
        statusCallbackOnline?.invoke()

        // Reconnect stoppen
        cancelReconnectHook()
    }

    // BACKEND-56: Debug-Funktion zum künstlichen Trennen
    fun forceDisconnectForDebug() {
        android.util.Log.w("WS_SERVICE", "Force-disconnect triggered (debug)")
        ws?.close(1000, "debug force disconnect")
    }

    // BACKEND-57: echter Reconnect-Flow (final state-machine integration)
    fun reconnectFlow(trigger: String) {
        android.util.Log.w("WS_SERVICE", "Starting reconnectFlow() — trigger=$trigger")

        // 1) Session vorher auf CONNECTING setzen
        com.securecall.app.ghostnet.session.GhostNetSession.get()
            .setState(com.securecall.app.ghostnet.session.GhostNetSessionState.CONNECTING)

        // 2) Transport stoppen (falls noch aktiv)
        try {
            com.securecall.app.ghostnet.transport.GhostTransport.get().stop()
        } catch (_: Throwable) {}

        // 3) Alte WS schließen
        try {
            ws?.close(1000, "reconnectFlow cleanup")
        } catch (_: Throwable) {}

        // 4) Neue Verbindung starten
        connect()

        android.util.Log.d("WS_SERVICE", "reconnectFlow() completed")
    }

    companion object {
        @Volatile
        var instance: WebSocketService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

        // BACKEND-57: Session auf ACTIVE setzen
        com.securecall.app.ghostnet.session.GhostNetSession.get()
            .setState(com.securecall.app.ghostnet.session.GhostNetSessionState.ACTIVE)

    // BACKEND-58: Transport Re-Init nach erfolgreichem WS-Connect
    private fun reinitTransportLayer() {
        try {
            com.securecall.app.ghostnet.transport.GhostTransport.get().reinitAfterReconnect()
        } catch (t: Throwable) {
            android.util.Log.e("WS_SERVICE", "Transport reinit failed", t)
        }
    }

        // BACKEND-58: Transport neu initialisieren
        reinitTransportLayer()

    // PATCH 201: Signaling für Key-Exchange
    private fun handleKeyExchange(json: String) {
        val parsed = com.securecall.app.net.signal.SignalParser.parse(json) ?: return
        val (type, remotePub) = parsed

        when (type) {

            "key-offer" -> {
                android.util.Log.d("WS_SERVICE", "Received key-offer → sending key-answer")
                // lokale Public Key holen
                val localPub =
                    com.securecall.app.ghostnet.keys.GhostNetKeyMaterial.getLocalPub()

                // Antwort senden
                val answer =
                    com.securecall.app.net.signal.KeyAnswer(localPub).toJson()
                ws?.send(answer)

                // Handshake Incoming starten
                com.securecall.app.ghostnet.session.GhostNetSession.get()
                    .acceptIncomingHandshake(remotePub)
            }

            "key-answer" -> {
                android.util.Log.d("WS_SERVICE", "Received key-answer → completing handshake")
                com.securecall.app.ghostnet.session.GhostNetSession.get()
                    .startOutgoingHandshake(remotePub)
            }
        }
    }

    // PATCH 201: onMessage erweitern
    override fun onMessage(text: String) {
        android.util.Log.d("WS_SERVICE", "onMessage(): $text")

        // Versuchen ob es ein Key-Exchange ist
        val parsed = com.securecall.app.net.signal.SignalParser.parse(text)
        if (parsed != null) {
            handleKeyExchange(text)
            return
        }

        // bisherige Handler
        listener.onMessage(text)
    }

    // PATCH 204: Call-Signaling Empfang einbauen
    override fun onMessage(webSocket: WebSocket, text: String) {
        android.util.Log.d("WS_SERVICE", "onMessage(): $text")

        // Key-Exchange zuerst prüfen
        val parsedKey = com.securecall.app.net.signal.SignalParser.parse(text)
        if (parsedKey != null) {
            handleKeyExchange(text)
            return
        }

        // Call-Signaling prüfen
        val parsedCall = com.securecall.app.net.signal.CallSignalParser.parse(text)
        if (parsedCall != null) {
            val (type, callId) = parsedCall
            when (type) {
                "call-init" -> {
                    android.util.Log.d("WS_SERVICE", "Received call-init: $callId")
                    com.securecall.app.call.CallController.INSTANCE.incomingCall(callId)
                }
                "call-bye" -> {
                    android.util.Log.d("WS_SERVICE", "Received call-bye: $callId")
                    com.securecall.app.call.CallController.INSTANCE.endCall()
                }
                else -> {
                    android.util.Log.w("WS_SERVICE", "Unknown call-signal type: $type")
                }
            }
            return
        }

        // Alle anderen Nachrichten
        listener.onMessage(text)
    }

package com.securecall.app.ghostnet.session

import android.util.Log
import com.securecall.app.ghostnet.transport.GhostTransport

// BACKEND-41 / ANDROID-03:
// Globale Session-Steuerung
class GhostNetSession private constructor() {

    companion object {
        private var instance: GhostNetSession? = null

        fun get(): GhostNetSession {
            if (instance == null) {
                instance = GhostNetSession()
            }
            return instance!!
        }
    }

    @Volatile
    private var state: GhostNetSessionState = GhostNetSessionState.IDLE

    private val TAG = "GHOST_SESSION"

    fun setState(newState: GhostNetSessionState) {
        if (state == newState) return
        Log.d(TAG, "Session state: $state → $newState")
        state = newState

        when (newState) {
            GhostNetSessionState.ACTIVE -> startTransport()
            GhostNetSessionState.DEAD -> stopTransport()
            else -> {} // IDLE, CONNECTING – nichts tun
        }
    }

    fun getState(): GhostNetSessionState = state

    private fun startTransport() {
        Log.d(TAG, "Starting transport layer (ACTIVE)")
        GhostTransport.get().startTransport()
    }

    private fun stopTransport() {
        Log.d(TAG, "Stopping transport layer (DEAD)")
        GhostTransport.get().stopTransport()
    }
}

    // BACKEND-54: KeepAliveEngine an Session binden
    private fun startKeepAlive() {
        com.securecall.app.ghostnet.session.keepalive.KeepAliveEngine.start()
    }

    private fun stopKeepAlive() {
        com.securecall.app.ghostnet.session.keepalive.KeepAliveEngine.stop()
    }

        when (newState) {
            GhostNetSessionState.ACTIVE -> {
                startTransport()
                startKeepAlive()
            }
            GhostNetSessionState.DEAD -> {
                stopKeepAlive()
                stopTransport()
            }
            else -> {}
        }

    // BACKEND-54: KeepAliveEngine an Session binden
    private fun startKeepAlive() {
        com.securecall.app.ghostnet.session.keepalive.KeepAliveEngine.start()
    }

    private fun stopKeepAlive() {
        com.securecall.app.ghostnet.session.keepalive.KeepAliveEngine.stop()
    }

        when (newState) {
            GhostNetSessionState.ACTIVE -> {
                startTransport()
                startKeepAlive()
            }
            GhostNetSessionState.DEAD -> {
                stopKeepAlive()
                stopTransport()
            }
            else -> {}
        }

        // BACKEND-55: Reconnect-Hooks nach State-Wechsel
        if (newState == GhostNetSessionState.DEAD) {
            com.securecall.app.ghostnet.session.reconnect.GhostNetReconnector.onSessionDead()
        }
        if (newState == GhostNetSessionState.ACTIVE) {
            com.securecall.app.ghostnet.session.reconnect.GhostNetReconnector.cancel()
        }

        // BACKEND-58: nach Transport-Init: Session wieder ACTIVE
        if (newState == GhostNetSessionState.CONNECTING) {
            // später evtl weiter differenzieren
            setState(GhostNetSessionState.ACTIVE)
        }

    // BACKEND-60: SharedFlow für State-Events
    private val _stateFlow = kotlinx.coroutines.flow.MutableStateFlow(state)
    val stateFlow: kotlinx.coroutines.flow.StateFlow<GhostNetSessionState>
        get() = _stateFlow

    // BACKEND-60: Broadcast an Flow-Observer
    private fun broadcastState(newState: GhostNetSessionState) {
        try {
            _stateFlow.value = newState
        } catch (t: Throwable) {
            android.util.Log.e("GHOST_SESSION", "Failed to update stateFlow", t)
        }
    }

        // BACKEND-60: Broadcast-Event an StateFlow
        broadcastState(newState)

    // BACKEND-65: Platzhalter für zukünftigen Session-Key-Setup
    fun performKeyExchange(localPriv: ByteArray, remotePub: ByteArray) {
        android.util.Log.d("GHOST_SESSION", "performKeyExchange(): placeholder")
        com.securecall.app.ghostnet.session.SessionKeyController.deriveSessionKey(localPriv, remotePub)
    }

    // BACKEND-66: Schlüsselmaterial vorbereiten (Placeholder)
    fun prepareKeyMaterial() {
        android.util.Log.d("GHOST_SESSION", "prepareKeyMaterial(): generating ephemeral keys")
        com.securecall.app.ghostnet.keys.GhostNetKeyMaterial.generateEphemeralKeypair()
    }

    // PATCH 200: Outgoing-Handshake starten
    fun startOutgoingHandshake(remotePub: ByteArray) {
        android.util.Log.d("GHOST_SESSION", "startOutgoingHandshake()")

        setState(GhostNetSessionState.CONNECTING)
        com.securecall.app.ghostnet.handshake.HandshakeController.startOutgoing(remotePub)

        val hsState = com.securecall.app.ghostnet.handshake.HandshakeController.getState()
        if (hsState == com.securecall.app.ghostnet.handshake.HandshakeState.ESTABLISHED) {
            android.util.Log.d("GHOST_SESSION", "Handshake established → ACTIVE")
            setState(GhostNetSessionState.ACTIVE)
        } else {
            android.util.Log.e("GHOST_SESSION", "Handshake failed → DEAD")
            setState(GhostNetSessionState.DEAD)
        }
    }

    // PATCH 200: Incoming-Handshake akzeptieren
    fun acceptIncomingHandshake(remotePub: ByteArray) {
        android.util.Log.d("GHOST_SESSION", "acceptIncomingHandshake()")

        setState(GhostNetSessionState.CONNECTING)
        com.securecall.app.ghostnet.handshake.HandshakeController.acceptIncoming(remotePub)

        val hsState = com.securecall.app.ghostnet.handshake.HandshakeController.getState()
        if (hsState == com.securecall.app.ghostnet.handshake.HandshakeState.ESTABLISHED) {
            android.util.Log.d("GHOST_SESSION", "Incoming handshake OK → ACTIVE")
            setState(GhostNetSessionState.ACTIVE)
        } else {
            android.util.Log.e("GHOST_SESSION", "Incoming handshake FAIL → DEAD")
            setState(GhostNetSessionState.DEAD)
        }
    }

    // PATCH 205: Call wurde aktiv
    fun onCallActive() {
        android.util.Log.d("GHOST_SESSION", "onCallActive(): enabling media pipeline")
        // später: Transport.startAudio(), startMediaDecryptor(), etc.
        if (state == GhostNetSessionState.CONNECTING || state == GhostNetSessionState.ACTIVE) {
            setState(GhostNetSessionState.ACTIVE)
        }
    }

    // PATCH 205: Call wurde beendet
    fun onCallEnded() {
        android.util.Log.d("GHOST_SESSION", "onCallEnded(): shutting down session")
        // später: Transport.stopAudio(), wipe keys, stop routing, etc.
        setState(GhostNetSessionState.DEAD)
    }

    // PATCH 206 — Transport aktivieren
    fun enableTransport() {
        android.util.Log.d("GHOST_SESSION", "enableTransport(): starting transport")
        com.securecall.app.ghostnet.transport.GhostTransport.get().start()
    }

    // PATCH 206 — Transport deaktivieren
    fun disableTransport() {
        android.util.Log.d("GHOST_SESSION", "disableTransport(): stopping transport")
        com.securecall.app.ghostnet.transport.GhostTransport.get().stop()
    }

    // Integration in Call-Lifecycle
    override fun onCallActive() {
        android.util.Log.d("GHOST_SESSION", "onCallActive(): enabling transport + session active")
        enableTransport()
        setState(GhostNetSessionState.ACTIVE)
    }

    override fun onCallEnded() {
        android.util.Log.d("GHOST_SESSION", "onCallEnded(): disabling transport + session dead")
        disableTransport()
        setState(GhostNetSessionState.DEAD)
    }

    // PATCH 208 — MediaPipeline aktivieren
    private fun enableMediaPipeline() {
        android.util.Log.d("GHOST_SESSION", "enableMediaPipeline(): starting media pipeline")
        com.securecall.app.ghostnet.media.GhostMediaPipeline.start()
    }

    // PATCH 208 — MediaPipeline deaktivieren
    private fun disableMediaPipeline() {
        android.util.Log.d("GHOST_SESSION", "disableMediaPipeline(): stopping media pipeline")
        com.securecall.app.ghostnet.media.GhostMediaPipeline.stop()
    }

    // PATCH 208 — Integration in Call-Lifecycle (Erweiterung)
    override fun onCallActive() {
        android.util.Log.d("GHOST_SESSION", "onCallActive(): enabling transport + media + session active")
        enableTransport()
        enableMediaPipeline()
        setState(GhostNetSessionState.ACTIVE)
    }

    override fun onCallEnded() {
        android.util.Log.d("GHOST_SESSION", "onCallEnded(): stopping media + transport + session dead")
        disableMediaPipeline()
        disableTransport()
        setState(GhostNetSessionState.DEAD)
    }

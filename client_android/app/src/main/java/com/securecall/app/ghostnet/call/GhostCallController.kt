package com.securecall.app.ghostnet.call

import android.util.Log

/**
 * PATCH 229:
 * Zentrale Steuerung für den Call-Status.
 * Noch keine tiefe Integration in Transport/Media – reine Architekturvorbereitung.
 */
object GhostCallController {

    private const val TAG = "GHOST_CALL"

    private var state: GhostCallState = GhostCallState.IDLE

    fun getState(): GhostCallState = state

    fun startOutgoingCall() {
        if (state != GhostCallState.IDLE && state != GhostCallState.ENDED) {
            Log.w(TAG, "startOutgoingCall(): ignored, state=$state")
            return
        }
        state = GhostCallState.ESTABLISHING
        Log.d(TAG, "startOutgoingCall(): state=$state")
        // später: Transport/Signaling anwerfen
    }

    fun markCallActive() {
        if (state != GhostCallState.ESTABLISHING) {
            Log.w(TAG, "markCallActive(): unexpected from state=$state")
        }
        state = GhostCallState.ACTIVE
        Log.d(TAG, "markCallActive(): state=$state")
    }

    fun terminateCall() {
        if (state == GhostCallState.IDLE || state == GhostCallState.ENDED) {
            Log.w(TAG, "terminateCall(): ignored, state=$state")
            return
        }
        state = GhostCallState.TERMINATING
        Log.d(TAG, "terminateCall(): state=$state")
        // später: Media/Transport/Signaling geordnet herunterfahren
    }

    fun markCallEnded() {
        state = GhostCallState.ENDED
        Log.d(TAG, "markCallEnded(): state=$state")
    }

    fun hardReset() {
        state = GhostCallState.IDLE
        Log.d(TAG, "hardReset(): state=$state")
    }
}

    // PATCH 231 — global quiet shutdown
    fun performQuietShutdown() {
        android.util.Log.d(TAG, "performQuietShutdown(): executing quiet call shutdown")

        // stop transport
        com.securecall.app.ghostnet.transport.GhostTransport.quietStop()

        // stop media pipeline
        com.securecall.app.ghostnet.media.GhostMediaRouter.get().quietShutdown()

        // finalize
        state = GhostCallState.ENDED
        android.util.Log.d(TAG, "performQuietShutdown(): state=$state")
    }

    // PATCH 231 — auto quiet shutdown when terminating
    private fun onTerminate() {
        performQuietShutdown()
    }

        // PATCH 231 — hook quiet shutdown
        onTerminate()

    // PATCH 234: Call establishing → session negotiating
    private fun syncSessionNegotiating() {
        val s = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        s.setState(com.securecall.app.ghostnet.session.GhostNetSessionState.NEGOTIATING)
    }

        // PATCH 234: call start → session negotiating
        syncSessionNegotiating()

    // PATCH 234: Call active → session active
    private fun syncSessionActive() {
        val s = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        s.setState(com.securecall.app.ghostnet.session.GhostNetSessionState.ACTIVE)
    }

        // PATCH 234: call active → session active
        syncSessionActive()

    // PATCH 234: Call terminating → session terminating
    private fun syncSessionTerminating() {
        val s = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        s.setState(com.securecall.app.ghostnet.session.GhostNetSessionState.TERMINATING)
    }

        // PATCH 234: call terminating → session terminating
        syncSessionTerminating()

    // PATCH 234: Call ended → session dead
    private fun syncSessionEnded() {
        val s = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        s.setState(com.securecall.app.ghostnet.session.GhostNetSessionState.DEAD)
    }

        // PATCH 234: call ended → session dead
        syncSessionEnded()

    // PATCH 236: Listener system
    private val listeners = mutableListOf<CallStateListener>()

    fun addListener(l: CallStateListener) {
        listeners.add(l)
    }
    fun removeListener(l: CallStateListener) {
        listeners.remove(l)
    }

    private fun notifyListeners() {
        for (l in listeners) {
            l.onCallStateChanged(state)
        }
    }

        // PATCH 236: notify listeners on call-state change
        notifyListeners()

// PATCH 237: full-stack reset
fun fullReset() {
    android.util.Log.w("GHOST_CALL", "fullReset(): resetting call + media + transport + session")

    // 1. Call zurücksetzen
    state = GhostCallState.IDLE
    notifyListeners()

    // 2. Transport beenden
    com.securecall.app.ghostnet.transport.GhostTransport.resetTransport()

    // 3. Media zurücksetzen
    com.securecall.app.ghostnet.media.GhostMediaRouter.resetMedia()

    // 4. Session zurücksetzen
    com.securecall.app.ghostnet.session.GhostNetSessionManager.resetSession()
}

        // PATCH 253: CryptoContext bei FullReset löschen
        com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.clearContext()
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "FullReset → CryptoContext cleared")

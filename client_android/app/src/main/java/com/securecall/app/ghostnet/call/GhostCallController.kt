package com.securecall.app.ghostnet.call

import android.util.Log

/**
 * PATCH 229 / 231 / 234 / 236 / 237 / 253:
 * Zentrale Steuerung für den Call-Status.
 */
object GhostCallController {

    private const val TAG = "GHOST_CALL"

    private var state: GhostCallState = GhostCallState.IDLE

    // PATCH 236: Listener system
    private val listeners = mutableListOf<CallStateListener>()

    fun getState(): GhostCallState = state

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

    fun startOutgoingCall() {
        if (state != GhostCallState.IDLE && state != GhostCallState.ENDED) {
            Log.w(TAG, "startOutgoingCall(): ignored, state=$state")
            return
        }
        state = GhostCallState.ESTABLISHING
        Log.d(TAG, "startOutgoingCall(): state=$state")
        syncSessionNegotiating()
        notifyListeners()
    }

    fun markCallActive() {
        if (state != GhostCallState.ESTABLISHING) {
            Log.w(TAG, "markCallActive(): unexpected from state=$state")
        }
        state = GhostCallState.ACTIVE
        Log.d(TAG, "markCallActive(): state=$state")
        syncSessionActive()
        notifyListeners()
    }

    fun terminateCall() {
        if (state == GhostCallState.IDLE || state == GhostCallState.ENDED) {
            Log.w(TAG, "terminateCall(): ignored, state=$state")
            return
        }
        state = GhostCallState.TERMINATING
        Log.d(TAG, "terminateCall(): state=$state")
        syncSessionTerminating()
        notifyListeners()
        onTerminate()
    }

    fun markCallEnded() {
        state = GhostCallState.ENDED
        Log.d(TAG, "markCallEnded(): state=$state")
        syncSessionEnded()
        notifyListeners()
    }

    fun hardReset() {
        state = GhostCallState.IDLE
        Log.d(TAG, "hardReset(): state=$state")
    }

    // PATCH 231: global quiet shutdown
    fun performQuietShutdown() {
        Log.d(TAG, "performQuietShutdown(): executing quiet call shutdown")
        com.securecall.app.ghostnet.transport.GhostTransport.quietStop()
        com.securecall.app.ghostnet.media.GhostMediaRouter.quietShutdown()
        state = GhostCallState.ENDED
        Log.d(TAG, "performQuietShutdown(): state=$state")
    }

    private fun onTerminate() {
        performQuietShutdown()
    }

    // PATCH 234: Session-Sync Helpers
    private fun syncSessionNegotiating() {
        val s = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        s.setState(com.securecall.app.ghostnet.session.GhostNetSessionState.NEGOTIATING)
    }

    private fun syncSessionActive() {
        val s = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        s.setState(com.securecall.app.ghostnet.session.GhostNetSessionState.ACTIVE)
    }

    private fun syncSessionTerminating() {
        val s = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        s.setState(com.securecall.app.ghostnet.session.GhostNetSessionState.TERMINATING)
    }

    private fun syncSessionEnded() {
        val s = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        s.setState(com.securecall.app.ghostnet.session.GhostNetSessionState.DEAD)
    }

    // PATCH 237: full-stack reset
    fun fullReset() {
        Log.w(TAG, "fullReset(): resetting call + media + transport + session")

        state = GhostCallState.IDLE
        notifyListeners()

        com.securecall.app.ghostnet.transport.GhostTransport.resetTransport()
        com.securecall.app.ghostnet.media.GhostMediaRouter.resetMedia()
        com.securecall.app.ghostnet.session.GhostNetSessionManager.resetSession()

        // PATCH 253: CryptoContext bei FullReset löschen
        com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.clearContext()
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "FullReset → CryptoContext cleared")
    }
}

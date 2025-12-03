package com.securecall.app.ghostnet.session

import android.util.Log

/**
 * PATCH 233:
 * Minimale GhostNet Session.
 * Noch ohne Crypto, Keys, Peer-ID etc.
 */
class GhostNetSession(
    val sessionId: String
) {

    private var state: GhostNetSessionState = GhostNetSessionState.IDLE

    fun setState(newState: GhostNetSessionState) {
        Log.d("GHOST_SESSION", "Session $sessionId: $state → $newState")
        state = newState
    }

    fun getState(): GhostNetSessionState = state
}

        // PATCH 236: event hook
        com.securecall.app.ghostnet.session.GhostNetSessionManager.notifyListeners(newState)

// PATCH 237: auto-clean when session becomes DEAD
if (newState == com.securecall.app.ghostnet.session.GhostNetSessionState.DEAD) {
    android.util.Log.e("SESSION", "Session moved to DEAD → auto reset")
    com.securecall.app.ghostnet.session.GhostNetSessionManager.resetSession()
}

        // PATCH 253: Crypto-Kontext löschen, wenn Session endet
        if (newState == com.securecall.app.ghostnet.session.GhostNetSessionState.DEAD) {
            com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.clearContext()
            com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Session DEAD → CryptoContext cleared")
        }

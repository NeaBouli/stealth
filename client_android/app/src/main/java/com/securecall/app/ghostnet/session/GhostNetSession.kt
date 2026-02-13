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

        // PATCH 236: notify listeners
        GhostNetSessionManager.notifyListeners(newState)

        // PATCH 237: auto-clean when session becomes DEAD
        if (newState == GhostNetSessionState.DEAD) {
            Log.e("SESSION", "Session moved to DEAD → auto reset")
            // PATCH 253: Crypto-Kontext löschen
            com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.clearContext()
            com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Session DEAD → CryptoContext cleared")
            GhostNetSessionManager.resetSession()
        }
    }

    fun getState(): GhostNetSessionState = state
}

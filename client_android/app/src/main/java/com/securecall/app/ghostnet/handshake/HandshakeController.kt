package com.securecall.app.ghostnet.handshake

import android.util.Log
import com.securecall.app.ghostnet.keys.GhostNetKeyMaterial
import com.securecall.app.ghostnet.session.SessionKeyController

/**
 * PATCH 199:
 * Skeleton für einen sehr einfachen Handshake-Controller.
 *
 * Später:
 *  - echtes X3DH / Noise-Handshake
 *  - Austausch von Public Keys über Signaling
 *  - Fehlerbehandlung / Retries
 */
object HandshakeController {

    private const val TAG = "HANDSHAKE_CTRL"

    private var state: HandshakeState = HandshakeState.IDLE

    fun getState(): HandshakeState = state

    fun reset() {
        Log.d(TAG, "reset()")
        state = HandshakeState.IDLE
        SessionKeyController.wipe()
        GhostNetKeyMaterial.wipeEphemeral()
    }

    /**
     * Outgoing Handshake (Platzhalter):
     *  - generiert Ephemeral Keys
     *  - leitet Session-Key aus Fake-LocalPriv + RemotePub ab
     */
    fun startOutgoing(remotePub: ByteArray) {
        Log.d(TAG, "startOutgoing(): called")
        state = HandshakeState.OUTGOING

        // MVP: Fake-Flow
        GhostNetKeyMaterial.generateEphemeralKeypair()
        val localPriv = GhostNetKeyMaterial.getEphemeralPriv()
        if (localPriv == null) {
            Log.e(TAG, "startOutgoing(): ephPriv is null, failing")
            state = HandshakeState.FAILED
            return
        }

        SessionKeyController.deriveSessionKey(localPriv, remotePub)

        if (SessionKeyController.hasSessionKey()) {
            Log.d(TAG, "startOutgoing(): session key established")
            state = HandshakeState.ESTABLISHED
        } else {
            Log.e(TAG, "startOutgoing(): session key missing → FAILED")
            state = HandshakeState.FAILED
        }
    }

    /**
     * Incoming Handshake (Platzhalter):
     *  - generiert Ephemeral Keys
     *  - nutzt RemotePub als Gegenpart
     */
    fun acceptIncoming(remotePub: ByteArray) {
        Log.d(TAG, "acceptIncoming(): called")
        state = HandshakeState.INCOMING

        GhostNetKeyMaterial.generateEphemeralKeypair()
        val localPriv = GhostNetKeyMaterial.getEphemeralPriv()
        if (localPriv == null) {
            Log.e(TAG, "acceptIncoming(): ephPriv is null, failing")
            state = HandshakeState.FAILED
            return
        }

        SessionKeyController.deriveSessionKey(localPriv, remotePub)

        if (SessionKeyController.hasSessionKey()) {
            Log.d(TAG, "acceptIncoming(): session key established")
            state = HandshakeState.ESTABLISHED
        } else {
            Log.e(TAG, "acceptIncoming(): session key missing → FAILED")
            state = HandshakeState.FAILED
        }
    }
}

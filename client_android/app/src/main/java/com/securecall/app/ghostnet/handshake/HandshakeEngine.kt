package com.securecall.app.ghostnet.handshake

import android.util.Log
import java.security.SecureRandom

/**
 * PATCH 245:
 * Skeleton für GhostNet Handshake Engine.
 *
 * Ziel:
 *  - Erzeugt "Ephemeral Keypair"
 *  - Simuliert "Shared Secret"
 *  - Liefert das Material an SessionKeyDerivation weiter
 *
 * Noch KEINE echte Kryptographie!
 * Die echte Version (X25519/Noise) folgt in CRYPTO-02 .. CRYPTO-04.
 */

data class HandshakeResult(
    val sharedSecret: ByteArray,
    val localEphemeralPub: ByteArray,
    val remoteEphemeralPub: ByteArray,
    val timestamp: Long
)

object HandshakeEngine {

    private const val TAG = "HSK_ENGINE"
    private val rnd = SecureRandom()

    /**
     * Simulierter „Handshake“ – Platzhalter:
     * sharedSecret = Random(32)
     * localPub / remotePub = Random(32)
     */
    fun performMockHandshake(): HandshakeResult {
        Log.d(TAG, "performMockHandshake(): PLACEHOLDER handshake running")

        val localPub = ByteArray(32)
        val remotePub = ByteArray(32)
        val shared = ByteArray(32)

        rnd.nextBytes(localPub)
        rnd.nextBytes(remotePub)
        rnd.nextBytes(shared)

        return HandshakeResult(
            sharedSecret = shared,
            localEphemeralPub = localPub,
            remoteEphemeralPub = remotePub,
            timestamp = System.currentTimeMillis()
        )
    }
}

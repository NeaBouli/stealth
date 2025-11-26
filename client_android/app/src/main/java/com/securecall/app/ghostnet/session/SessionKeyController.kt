package com.securecall.app.ghostnet.session

import android.util.Log
import com.securecall.crypto.CoreCrypto

/**
 * PATCH 197 / BACKEND-65:
 * Zentrale Stelle zur Verwaltung von Session Keys für GhostNet.
 *
 * Später:
 *  - Noise / DoubleRatchet / X3DH
 *  - PQC (Kyber / SABER)
 *  - Secure key wipe
 */
object SessionKeyController {

    private const val TAG = "SESSION_KEY_CTRL"

    // aktueller Session Key (32 Bytes)
    private var sessionKey: ByteArray? = null

    fun hasSessionKey(): Boolean = sessionKey != null

    fun deriveSessionKey(localPriv: ByteArray, remotePub: ByteArray) {
        Log.d(TAG, "deriveSessionKey(): deriving…")

        val derived = CoreCrypto.deriveSessionKey(localPriv, remotePub)

        if (derived == null || derived.isEmpty()) {
            Log.e(TAG, "deriveSessionKey(): ERROR – derive returned null/empty")
            sessionKey = null
            return
        }

        sessionKey = derived
        Log.d(TAG, "deriveSessionKey(): key established (${derived.size} bytes)")
    }

    fun getKey(): ByteArray? = sessionKey

    fun wipe() {
        if (sessionKey != null) {
            Log.d(TAG, "wipe(): wiping session key")
            sessionKey?.fill(0)
            sessionKey = null
        }
    }
}

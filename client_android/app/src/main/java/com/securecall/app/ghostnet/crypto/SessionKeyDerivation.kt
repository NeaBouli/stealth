package com.securecall.app.ghostnet.crypto

import android.util.Log
import com.securecall.app.ghostnet.handshake.HandshakeEngine
import com.securecall.app.ghostnet.handshake.HandshakeResult
import java.security.SecureRandom

/**
 * PATCH 242 + 246:
 * Skeleton für Session-Key-Ableitung.
 *
 * Aktueller Status:
 *  - PLACEHOLDER-Implementierung (KEINE echte Kryptographie)
 *  - SessionKeys-Datenklasse
 *  - deriveEphemeral(): reine Random-Keys
 *  - deriveFromSharedSecret(): nimmt ein „sharedSecret“ als Input (HKDF-Platzhalter)
 *  - deriveFromMockHandshake(): koppelt die HandshakeEngine an die Key-Ableitung
 *
 * Später (CRYPTO-02ff.):
 *  - X25519 / Noise-Handshakes
 *  - HKDF-basiertes Key-Material
 *  - XChaCha20-Poly1305 / AES-GCM
 */

data class SessionKeys(
    val rxKey: ByteArray,
    val txKey: ByteArray,
    val salt: ByteArray,
    val createdAtMillis: Long
)

object SessionKeyDerivation {

    private const val TAG = "SESSION_KEYS"
    private val rnd = SecureRandom()

    /**
     * PATCH 242:
     * Erzeugt placeholder-Schlüssel für eine „Ephemeral Session“.
     *
     * Wird aktuell unabhängig von einem Handshake genutzt.
     */
    fun deriveEphemeral(): SessionKeys {
        Log.d(TAG, "deriveEphemeral(): PLACEHOLDER – generating random keys")

        val salt = ByteArray(32)
        val rx = ByteArray(32)
        val tx = ByteArray(32)

        rnd.nextBytes(salt)
        rnd.nextBytes(rx)
        rnd.nextBytes(tx)

        return SessionKeys(
            rxKey = rx,
            txKey = tx,
            salt = salt,
            createdAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * PATCH 246:
     * Ableitung aus einem „sharedSecret“.
     *
     * Später:
     *   - HKDF(sharedSecret, salt, info) → rx/tx
     *
     * Jetzt:
     *   - sharedSecret wird nur geloggt, Keys bleiben pseudo-random.
     */
    fun deriveFromSharedSecret(sharedSecret: ByteArray): SessionKeys {
        Log.d(
            TAG,
            "deriveFromSharedSecret(): PLACEHOLDER – sharedSecret.size=" + sharedSecret.size
        )

        // TODO (CRYPTO-03): HKDF o.ä. einsetzen, statt reinem Random.
        val salt = ByteArray(32)
        val rx = ByteArray(32)
        val tx = ByteArray(32)

        rnd.nextBytes(salt)
        rnd.nextBytes(rx)
        rnd.nextBytes(tx)

        return SessionKeys(
            rxKey = rx,
            txKey = tx,
            salt = salt,
            createdAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * PATCH 246:
     * Voller Mock-Flow:
     *   HandshakeEngine.performMockHandshake()
     *      → sharedSecret
     *      → deriveFromSharedSecret()
     *
     * Dient als Architektur-Vorlage für den echten Kryptopfad.
     */
    fun deriveFromMockHandshake(): SessionKeys {
        Log.d(TAG, "deriveFromMockHandshake(): starting mock handshake → key derivation")

        val result: HandshakeResult = HandshakeEngine.performMockHandshake()

        Log.d(
            TAG,
            "deriveFromMockHandshake(): got sharedSecret.size=" + result.sharedSecret.size
        )

        return deriveFromSharedSecret(result.sharedSecret)
    }
}

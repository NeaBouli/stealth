package com.securecall.app.ghostnet.keys

import android.util.Log
import java.security.SecureRandom

/**
 * PATCH 198 / BACKEND-66:
 * Placeholder-Keymaterial:
 *  - lokales Schlüsselpaar (priv/pub)
 *  - Ephemeral Keys pro Session
 *  - später ersetzt durch echte Rust/X25519 Implementierung
 */
object GhostNetKeyMaterial {

    private const val TAG = "KEY_MATERIAL"
    private val rng = SecureRandom()

    // ---- Lokales dauerhaftes Schlüsselpaar (MVP = Random Bytes) ----
    private var localPriv: ByteArray = ByteArray(32)
    private var localPub: ByteArray = ByteArray(32)

    // ---- Ephemeral Session Keys ----
    private var ephPriv: ByteArray? = null
    private var ephPub: ByteArray? = null

    init {
        rng.nextBytes(localPriv)
        rng.nextBytes(localPub)
        Log.d(TAG, "Generated fake local keypair (MVP placeholder)")
    }

    fun getLocalPriv(): ByteArray = localPriv
    fun getLocalPub(): ByteArray = localPub

    // ---- Ephemeral Keys generieren ----
    fun generateEphemeralKeypair() {
        val priv = ByteArray(32)
        val pub = ByteArray(32)
        rng.nextBytes(priv)
        rng.nextBytes(pub)
        ephPriv = priv
        ephPub = pub
        Log.d(TAG, "Ephemeral keypair generated (MVP random bytes)")
    }

    fun getEphemeralPriv(): ByteArray? = ephPriv
    fun getEphemeralPub(): ByteArray? = ephPub

    // ---- Ephemeral Keys löschen ----
    fun wipeEphemeral() {
        ephPriv?.fill(0)
        ephPub?.fill(0)
        ephPriv = null
        ephPub = null
        Log.d(TAG, "Ephemeral keypair wiped")
    }
}

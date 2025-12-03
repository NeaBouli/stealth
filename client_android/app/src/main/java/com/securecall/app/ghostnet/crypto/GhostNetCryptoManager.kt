package com.securecall.app.ghostnet.crypto

import android.util.Log

/**
 * PATCH 249:
 * Zentraler Manager für alle Crypto-Kontexte pro Session.
 *
 * Aktuell:
 *   - Platzhalter-Implementierung
 *   - Kann aus Mock-Handshake einen CryptoContext erzeugen
 *   - Bietet getContext() an
 *
 * Später (CRYPTO-03..05):
 *   - echte X25519/Noise-Handshakes
 *   - HKDF
 *   - XChaCha20-Poly1305
 *   - Key Rotation
 *   - Outbound/Inbound Pipelines
 */

object GhostNetCryptoManager {

    private const val TAG = "CRYPTO_MGR"

    private var currentContext: SessionCryptoContext? = null

    /**
     * Erzeugt einen neuen Kontext via Mock-Handshake.
     */
    fun createNewContext(): SessionCryptoContext {
        Log.d(TAG, "createNewContext(): using MockHandshake → SessionCryptoContext")
        val ctx = SessionCryptoContext.fromMockHandshake()
        currentContext = ctx
        return ctx
    }

    /**
     * Getter für den aktuellen Kontext.
     * Falls keiner existiert, wird einer erzeugt.
     */
    fun getContext(): SessionCryptoContext {
        if (currentContext == null) {
            Log.w(TAG, "getContext(): no context present → auto-create")
            currentContext = createNewContext()
        }
        return currentContext!!
    }

    /**
     * Löscht den aktuellen Kontext (z.B. bei Session-Ende).
     */
    fun clearContext() {
        Log.w(TAG, "clearContext(): CryptoContext removed")
        currentContext = null
    }
}

    // CRYPTO-04: neues Keypair erzeugen
    fun generateLocalECDHKeyPair() {
        val ctx = getContext()
        ctx.localKeyPair = FakeX25519.generateKeyPair()
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Local ECDH Keypair generated (FAKE)")
    }

    // CRYPTO-04: Remote-Key setzen (z.B. später aus Signaling)
    fun setRemotePublicKey(pub: ByteArray) {
        val ctx = getContext()
        ctx.remotePublicKey = pub
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Remote PublicKey set (len=${pub.size})")
    }

    // CRYPTO-04: Shared Secret ableiten
    fun deriveFakeSharedSecret() {
        val ctx = getContext()
        val local = ctx.localKeyPair?.privateKey
        val remote = ctx.remotePublicKey
        if (local == null || remote == null) {
            com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "deriveSharedSecret FAILED: missing keys")
            return
        }

        ctx.sharedSecret = FakeX25519.deriveSharedSecret(local, remote)
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Shared Secret derived (FAKE)")
    }

    // CRYPTO-05: Symmetrische Schlüssel aus sharedSecret ableiten
    fun deriveSymmetricKeysFromSharedSecret() {
        val ctx = getContext()
        val secret = ctx.sharedSecret

        if (secret == null) {
            com.securecall.app.debug.GhostDebugEventBus.post(
                "CRYPTO",
                "deriveSymmetricKeysFromSharedSecret: no sharedSecret set"
            )
            return
        }

        // Wir wollen 3 x 32 Byte: masterKey, sendKey, recvKey
        val outLen = 96
        val allKeys = HkdfSha256.deriveKeys(
            sharedSecret = secret,
            info = "SecureCall-GhostNet-Session",
            outLen = outLen
        )

        ctx.masterKey = allKeys.copyOfRange(0, 32)
        ctx.sendKey   = allKeys.copyOfRange(32, 64)
        ctx.recvKey   = allKeys.copyOfRange(64, 96)

        com.securecall.app.debug.GhostDebugEventBus.post(
            "CRYPTO",
            "HKDF derived: master/send/recv ready (CRYPTO-05)"
        )
    }

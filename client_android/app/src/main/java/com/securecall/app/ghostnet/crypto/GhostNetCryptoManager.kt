package com.securecall.app.ghostnet.crypto

import android.util.Log
import com.securecall.app.BuildConfig

/**
 * PATCH 249:
 * Zentraler Manager für alle Crypto-Kontexte pro Session.
 */
object GhostNetCryptoManager {

    private const val TAG = "CRYPTO_MGR"

    private var currentContext: SessionCryptoContext? = null

    fun createNewContext(): SessionCryptoContext {
        if (BuildConfig.DEBUG) Log.d(TAG, "createNewContext(): using MockHandshake → SessionCryptoContext")
        val ctx = SessionCryptoContext.fromMockHandshake()
        currentContext = ctx
        return ctx
    }

    fun getContext(): SessionCryptoContext {
        if (currentContext == null) {
            Log.w(TAG, "getContext(): no context present → auto-create")
            currentContext = createNewContext()
        }
        return currentContext!!
    }

    fun clearContext() {
        Log.w(TAG, "clearContext(): CryptoContext removed")
        currentContext?.wipe()
        currentContext = null
    }

    // CRYPTO-04: neues Keypair erzeugen (via Rust JNI)
    fun generateLocalECDHKeyPair() {
        val ctx = getContext()
        val raw = com.securecall.crypto.CoreCrypto.generateKeyPair()
        if (raw == null || raw.size != 64) {
            throw SecurityException("Failed to generate X25519 keypair via native crypto")
        }
        val priv = raw.copyOfRange(0, 32)
        val pub = raw.copyOfRange(32, 64)
        ctx.localKeyPair = X25519KeyPair(priv, pub)
        raw.fill(0)
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Local ECDH Keypair generated (native)")
    }

    // CRYPTO-04: Remote-Key setzen
    fun setRemotePublicKey(pub: ByteArray) {
        val ctx = getContext()
        ctx.remotePublicKey = pub
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Remote PublicKey set (len=${pub.size})")
    }

    // CRYPTO-04: Shared Secret ableiten (via Rust JNI)
    fun deriveSharedSecret() {
        val ctx = getContext()
        val local = ctx.localKeyPair?.privateKey
        val remote = ctx.remotePublicKey
        if (local == null || remote == null) {
            com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "deriveSharedSecret FAILED: missing keys")
            return
        }
        val derived = com.securecall.crypto.CoreCrypto.deriveSessionKey(local, remote)
        if (derived == null || derived.size != 32) {
            throw SecurityException("X25519 DH key derivation failed via native crypto")
        }
        ctx.sharedSecret = derived
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Shared Secret derived (native X25519)")
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

        val outLen = 96
        val allKeys = HkdfSha256.deriveKeys(
            sharedSecret = secret,
            info = "SecureCall-GhostNet-Session",
            outLen = outLen
        )

        ctx.masterKey = allKeys.copyOfRange(0, 32)
        ctx.sendKey = allKeys.copyOfRange(32, 64)
        ctx.recvKey = allKeys.copyOfRange(64, 96)

        com.securecall.app.debug.GhostDebugEventBus.post(
            "CRYPTO",
            "HKDF derived: master/send/recv ready (CRYPTO-05)"
        )
    }
}

package com.securecall.app.ghostnet

import android.util.Log

/**
 * PATCH 241:
 * Zentraler Initialisierer des GhostNet-Subsystems.
 */
object GhostNetSystem {

    private const val TAG = "GHOST_SYS"
    private var initialized = false

    fun init() {
        if (initialized) {
            Log.w(TAG, "GhostNetSystem.init(): already initialized")
            return
        }

        Log.d(TAG, "=== GhostNetSystem INIT START ===")

        val session = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        Log.d(TAG, "Session loaded: state=" + session.getState())

        val callState = com.securecall.app.ghostnet.call.GhostCallController.getState()
        Log.d(TAG, "Call controller initial state: $callState")

        com.securecall.app.ghostnet.media.GhostMediaRouter
        Log.d(TAG, "MediaRouter loaded")

        com.securecall.app.ghostnet.transport.GhostTransport
        Log.d(TAG, "Transport object created")

        com.securecall.app.debug.GhostDebugEventBus.post("SYSTEM", "GhostNetSystem initialized")

        // PATCH 250: CryptoContext beim Systemstart erzeugen
        initCrypto()

        // PATCH 251: CryptoContext in Subsysteme einspeisen
        wireCryptoIntoSubsystems()

        // PATCH 241: eventbus notice
        postInitEvent()

        initialized = true
        Log.d(TAG, "=== GhostNetSystem INIT DONE ===")
    }

    private fun postInitEvent() {
        com.securecall.app.debug.GhostDebugEventBus.post("SYSTEM", "GhostNetSystem fully initialized")
    }

    private fun initCrypto() {
        Log.d(TAG, "initCrypto(): creating initial SessionCryptoContext")
        val ctx = com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.getContext()
        com.securecall.app.debug.GhostDebugEventBus.post(
            "SYSTEM",
            "CryptoContext created: " + ctx.debugSummary()
        )
    }

    private fun wireCryptoIntoSubsystems() {
        val ctx = com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.getContext()
        com.securecall.app.ghostnet.transport.GhostTransport.setCryptoContext(ctx)
        com.securecall.app.ghostnet.media.GhostMediaRouter.attachCryptoContext(ctx)
        com.securecall.app.debug.GhostDebugEventBus.post("SYSTEM", "Crypto wired into Transport + Media")
    }
}

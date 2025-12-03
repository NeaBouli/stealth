package com.securecall.app.ghostnet

import android.util.Log

/**
 * PATCH 241:
 * Zentraler Initialisierer des GhostNet-Subsystems.
 * Sorgt für klar definierte Start-Reihenfolge und Logging.
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

        // 1) Session erzeugen
        val session = com.securecall.app.ghostnet.session.GhostNetSessionManager.get()
        Log.d(TAG, "Session loaded: state=" + session.getState())

        // 2) CallController auf Basiszustand
        val callState = com.securecall.app.ghostnet.call.GhostCallController.getState()
        Log.d(TAG, "Call controller initial state: $callState")

        // 3) MediaRouter init (falls benötigt)
        com.securecall.app.ghostnet.media.GhostMediaRouter.get()
        Log.d(TAG, "MediaRouter loaded")

        // 4) Transport instanzieren (aber noch nicht starten)
        com.securecall.app.ghostnet.transport.GhostTransport.get()
        Log.d(TAG, "Transport object created")

        // 5) Debug EventBus Info
        com.securecall.app.debug.GhostDebugEventBus.post("SYSTEM", "GhostNetSystem initialized")

        initialized = true
        Log.d(TAG, "=== GhostNetSystem INIT DONE ===")
    }
}

    // PATCH 241: eventbus system-level notification
    private fun postInitEvent() {
        com.securecall.app.debug.GhostDebugEventBus.post("SYSTEM", "GhostNetSystem fully initialized")
    }

        // PATCH 241: fire eventbus notice
        postInitEvent()

    // PATCH 250: CryptoContext beim Systemstart erzeugen
    private fun initCrypto() {
        android.util.Log.d(TAG, "initCrypto(): creating initial SessionCryptoContext")
        val ctx = com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.getContext()

        // Debug-Event ins EventBus
        com.securecall.app.debug.GhostDebugEventBus.post(
            "SYSTEM",
            "CryptoContext created: " + ctx.debugSummary()
        )
    }

        // PATCH 250: crypto init
        initCrypto()

    // PATCH 251: CryptoContext in Transport & Media einspeisen
    private fun wireCryptoIntoSubsystems() {
        val ctx = com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.getContext()

        // Transport informieren
        com.securecall.app.ghostnet.transport.GhostTransport.get().setCryptoContext(ctx)

        // MediaRouter informieren
        com.securecall.app.ghostnet.media.GhostMediaRouter.attachCryptoContext(ctx)

        com.securecall.app.debug.GhostDebugEventBus.post(
            "SYSTEM",
            "Crypto wired into Transport + Media"
        )
    }

        // PATCH 251: Crypto in Subsysteme weiterreichen
        wireCryptoIntoSubsystems()

        // PATCH 253: ensure new CryptoContext after system reset
        com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.clearContext()
        val freshCtx = com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.getContext()
        com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "GhostNetSystem → fresh CryptoContext: " + freshCtx.debugSummary())

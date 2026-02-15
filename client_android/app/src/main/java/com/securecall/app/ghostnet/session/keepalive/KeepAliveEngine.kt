package com.securecall.app.ghostnet.session.keepalive

import android.util.Log
import java.util.Timer
import java.util.TimerTask
import com.securecall.app.ghostnet.session.GhostNetSessionManager
import com.securecall.app.ghostnet.session.GhostNetSessionState

// BACKEND-54 / ANDROID-03:
// KeepAliveEngine — sendet periodisch PINGs bei aktiver Session
// und überwacht Deadlines (Timeout für PONG).

object KeepAliveEngine {

    private const val TAG = "KEEPALIVE"
    private const val INTERVAL_MS = 3000L          // alle 3 Sekunden
    private const val TIMEOUT_MS = 7000L           // kein Pong → DEAD

    private var timer: Timer? = null
    private var lastPongTimestamp = System.currentTimeMillis()

    fun start() {
        if (timer != null) return

        Log.d(TAG, "Starting KeepAliveEngine")
        timer = Timer()

        timer?.schedule(object : TimerTask() {
            override fun run() {
                val state = GhostNetSessionManager.get().getState()
                if (state != GhostNetSessionState.ACTIVE) return

                val now = System.currentTimeMillis()

                // Timeout prüfen
                if (now - lastPongTimestamp > TIMEOUT_MS) {
                    Log.w(TAG, "KeepAlive timeout → setting session DEAD")
                    GhostNetSessionManager.get().setState(GhostNetSessionState.DEAD)
                    return
                }

                // PING senden
                com.securecall.app.ghostnet.transport.GhostTransport.sendKeepAlive()
            }
        }, INTERVAL_MS, INTERVAL_MS)
    }

    fun updatePongReceived() {
        lastPongTimestamp = System.currentTimeMillis()
    }

    fun stop() {
        timer?.cancel()
        timer = null
        Log.d(TAG, "KeepAliveEngine stopped")
    }
}

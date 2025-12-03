package com.securecall.app.ghostnet.media

import android.util.Log
import kotlin.random.Random

/**
 * PATCH 239:
 * Dummy-Recorder generiert zufällige Bytes als "Audio".
 * Kein echtes Mikrofon, kein AudioRecord – reine Test-Pipeline.
 */
object DummyAudioRecorder {

    private const val TAG = "DUMMY_RECORDER"
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true

        thread = Thread {
            Log.d(TAG, "Dummy recorder started")

            while (running) {
                // 64 Bytes Pseudo-Audio
                val data = Random.nextBytes(64)
                val ts = System.currentTimeMillis()

                val frame = MediaFrame(data, ts)

                // in Transport schieben
                com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueTestFrame(data)

                Thread.sleep(25) // ca. 40 FPS
            }

            Log.d(TAG, "Dummy recorder stopped")
        }

        thread?.start()
    }

    fun stop() {
        running = false
        thread?.interrupt()
    }
}

    // PATCH 240: Debug-Event posten
    private fun postDebugEventGenerated(size: Int) {
        com.securecall.app.debug.GhostDebugEventBus.post(
            "DUMMY_REC",
            "generated test frame size=$size"
        )
    }

            // PATCH 240: EventBus Meldung nach Frame-Erzeugung
            postDebugEventGenerated(data.size)

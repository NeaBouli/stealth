package com.securecall.app.ghostnet.transport

import android.util.Log
import com.securecall.app.audio.capture.AudioCapturePlaceholder
import com.securecall.app.ghostnet.transport.frame.AudioFrameBuilder
import com.securecall.app.ghostnet.transport.queue.TransportFrameQueue
import com.securecall.app.ghostnet.transport.scheduler.TransportScheduler
import com.securecall.app.ghostnet.transport.thread.GhostTransportThread

// BACKEND-33..40 / ANDROID-03:
// Zentrale Transport-Fassade für GhostNet.
// - verwaltet Thread
// - verwaltet Scheduler
// - integriert (Platzhalter-)Audio-Capture
// - stellt Debug/Test-Hooks bereit
class GhostTransport private constructor() {

    companion object {
        private var instance: GhostTransport? = null

        fun get(): GhostTransport {
            if (instance == null) {
                instance = GhostTransport()
            }
            return instance!!
        }
    }

    // Gemeinsame FrameQueue
    private val frameQueue = TransportFrameQueue.get()

    // Transport-Thread (Placeholder)
    private var transportThread: GhostTransportThread? = null

    // Scheduler (pumpt regelmäßig Frames)
    private var scheduler: TransportScheduler? = null

    // Platzhalter-Audioaufnahme
    private val audioCapture = AudioCapturePlaceholder()

    // --- Öffentliche Lifecycle-API -----------------------------------------

    fun startTransport() {
        Log.d("GHOST_TRANSPORT", "startTransport(): starting capture + thread + scheduler")
        startAudioCapture()
        startTransportThread()
        startScheduler()
    }

    fun stopTransport() {
        Log.d("GHOST_TRANSPORT", "stopTransport(): stopping scheduler + thread + capture")
        stopScheduler()
        stopTransportThread()
        stopAudioCapture()
    }

    // --- Intern: Thread-Steuerung ------------------------------------------

    private fun startTransportThread() {
        if (transportThread == null) {
            transportThread = GhostTransportThread()
            transportThread?.start()
            Log.d("GHOST_TRANSPORT", "TransportThread started")
        }
    }

    private fun stopTransportThread() {
        transportThread?.stopThread()
        transportThread = null
        Log.d("GHOST_TRANSPORT", "TransportThread stopped")
    }

    // --- Intern: Scheduler-Steuerung ---------------------------------------

    private fun startScheduler() {
        if (scheduler == null) {
            scheduler = TransportScheduler()
            scheduler?.start()
            Log.d("GHOST_TRANSPORT", "Scheduler started")
        }
    }

    private fun stopScheduler() {
        scheduler?.stop()
        scheduler = null
        Log.d("GHOST_TRANSPORT", "Scheduler stopped")
    }

    // --- Intern: Audio-Capture-Steuerung -----------------------------------

    private fun startAudioCapture() {
        audioCapture.start()
    }

    private fun stopAudioCapture() {
        audioCapture.stop()
    }

    // --- Debug / Test-Hilfsfunktionen --------------------------------------

    // BACKEND-34: einfacher Dummy-Frame (ByteArray-only)
    fun enqueueTestFrame() {
        val fakeFrame = ByteArray(64) { 0x42 }
        frameQueue.enqueue(fakeFrame)
        Log.d("GHOST_TRANSPORT", "enqueueTestFrame(): enqueued raw dummy frame")
    }

    // BACKEND-38: AudioFrame (RAW) mit fixem Dummy-Inhalt
    fun enqueueTestAudioFrame() {
        val fake = ByteArray(128) { 0x11 }
        val frame = AudioFrameBuilder.raw(fake)
        frameQueue.enqueue(frame)
        Log.d("GHOST_TRANSPORT", "enqueueTestAudioFrame(): $frame")
    }

    // BACKEND-39: Builder-Test (Silence/Control-Frame)
    fun enqueueBuilderTest() {
        val frame = AudioFrameBuilder.silence()
        frameQueue.enqueue(frame)
        Log.d("GHOST_TRANSPORT", "enqueueBuilderTest(): $frame")
    }

    // BACKEND-40: Dummy-AudioCapture → Frame
    fun enqueueCapturedAudioFrame() {
        val fakePcm = audioCapture.generateFakeAudioFrame()
        val frame = AudioFrameBuilder.raw(fakePcm)
        frameQueue.enqueue(frame)
        Log.d("GHOST_TRANSPORT", "enqueueCapturedAudioFrame(): $frame")
    }
}

    // BACKEND-52: ControlFrame senden
    fun sendControlFrame(frame: ByteArray) {
        frameQueue.enqueue(frame)
        android.util.Log.d("GHOST_TRANSPORT", "sendControlFrame(): enqueued control frame (len=${frame.size})")
    }

    // BACKEND-52: Komfort-API für Standard-Commands
    fun sendPing() {
        val f = com.securecall.app.ghostnet.control.ControlFrameBuilder.ping()
        sendControlFrame(f)
    }

    fun sendPong() {
        val f = com.securecall.app.ghostnet.control.ControlFrameBuilder.pong()
        sendControlFrame(f)
    }

    fun sendMute() {
        val f = com.securecall.app.ghostnet.control.ControlFrameBuilder.mute()
        sendControlFrame(f)
    }

    fun sendUnmute() {
        val f = com.securecall.app.ghostnet.control.ControlFrameBuilder.unmute()
        sendControlFrame(f)
    }

    // BACKEND-53: Ping-Timestamp für Roundtrip
    private var lastPingTimestamp: Long = 0L

    fun sendPingWithTimestamp() {
        lastPingTimestamp = System.currentTimeMillis()
        val f = com.securecall.app.ghostnet.control.ControlFrameBuilder.ping()
        sendControlFrame(f)
        android.util.Log.d("GHOST_TRANSPORT", "PING sent at $lastPingTimestamp")
    }

    fun handlePongAck() {
        val now = System.currentTimeMillis()
        val rtt = now - lastPingTimestamp
        android.util.Log.d("GHOST_TRANSPORT", "PONG received → RTT=${rtt}ms")
    }

    // BACKEND-58: vollständiger Re-Init-Skeleton
    fun reinitAfterReconnect() {
        android.util.Log.w("GHOST_TRANSPORT", "=== REINIT START ===")

        try {
            stop()
        } catch (_: Throwable) {
            android.util.Log.e("GHOST_TRANSPORT", "stop() failed during reinit")
        }

        try {
            init()
        } catch (t: Throwable) {
            android.util.Log.e("GHOST_TRANSPORT", "init() failed during reinit", t)
        }

        android.util.Log.w("GHOST_TRANSPORT", "=== REINIT DONE ===")
    }

    // BACKEND-58: Exposed init() for reconnect flow
    fun initClean() {
        android.util.Log.d("GHOST_TRANSPORT", "initClean() called")
        init()
    }

    // BACKEND-59: Router-Rebind nach Transport-Neuinitialisierung
    private fun rebindRouter() {
        try {
            com.securecall.app.ghostnet.control.GhostControlRouter.rebind()
        } catch (t: Throwable) {
            android.util.Log.e("GHOST_TRANSPORT", "Router rebind failed", t)
        }
    }

    // PATCH 185: Router-Rebind am Ende des ReInit-Zyklus
    fun reinitAfterReconnect() {
        android.util.Log.w("GHOST_TRANSPORT", "=== REINIT START ===")

        try { stop() } catch (_: Throwable) {}

        try { init() } catch (t: Throwable) {
            android.util.Log.e("GHOST_TRANSPORT", "init() failed during reinit", t)
        }

        // ❤️ NEU: ROUTER-REBIND
        rebindRouter()

        android.util.Log.w("GHOST_TRANSPORT", "=== REINIT DONE ===")
    }

    // BACKEND-62: safe-enqueue für Control-Frames
    fun sendControlFrame(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            android.util.Log.e("GHOST_TRANSPORT", "sendControlFrame(): refusing empty control frame")
            return
        }
        queue.enqueue(bytes)
        android.util.Log.d("GHOST_TRANSPORT", "sendControlFrame(): enqueued control header=${bytes[0]}")
    }

    // BACKEND-63: Test-Media-Frames in Queue schieben
    fun enqueueTestFrame(data: ByteArray) {
        queue.enqueue(data)
        android.util.Log.d("GHOST_TRANSPORT", "enqueueTestFrame(): size=${data.size}")
    }

    // PATCH 206 — Transport Start/Stop Skeleton
    private var running = false

    fun start() {
        if (running) {
            android.util.Log.w("GHOST_TRANSPORT", "start(): already running")
            return
        }
        running = true
        android.util.Log.d("GHOST_TRANSPORT", "Transport STARTED")

        // später:
        // - Thread Pool starten
        // - AudioFrameRouter aktivieren
        // - Netzwerk-Sendepipeline öffnen
    }

    fun stop() {
        if (!running) {
            android.util.Log.w("GHOST_TRANSPORT", "stop(): already stopped")
            return
        }
        running = false
        android.util.Log.d("GHOST_TRANSPORT", "Transport STOPPED")

        // später:
        // - Threads beenden
        // - Queue flushen
        // - Decoder/Decryptor stoppen
    }

    fun isRunning(): Boolean = running

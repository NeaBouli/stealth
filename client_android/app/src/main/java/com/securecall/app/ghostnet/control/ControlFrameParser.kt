package com.securecall.app.ghostnet.control

import android.util.Log

// BACKEND-49 / ANDROID-03:
// Skelett für Steuerframes (Control Frames).
// Frame-Struktur (später):
// [0] = 0x02 (Control-Frame-Typ, kommt schon in FrameRouter an)
// [1] = Opcode (z.B. 0x01 = MUTE, 0x02 = UNMUTE, 0x03 = PING, 0x04 = PONG)
// [2..] = Payload (optional)
object ControlFrameParser {

    private const val TAG = "CONTROL_FRAME"

    fun parse(frame: ByteArray) {
        if (frame.size < 2) {
            Log.w(TAG, "ControlFrame too short (len=${frame.size})")
            return
        }

        val opcode = frame[1].toInt() and 0xFF

        when (opcode) {
            0x01 -> handleMute(frame)
            0x02 -> handleUnmute(frame)
            0x03 -> handlePing(frame)
            0x04 -> handlePong(frame)
            else -> handleUnknown(opcode, frame)
        }
    }

    private fun handleMute(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: MUTE [placeholder]")
        // später: AudioCapture/Output stumm schalten
    }

    private fun handleUnmute(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: UNMUTE [placeholder]")
        // später: AudioCapture/Output wieder aktivieren
    }

    private fun handlePing(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: PING [placeholder]")
        // später: Antwort mit PONG über Transport
    }

    private fun handlePong(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: PONG [placeholder]")
        // später: Latenz/Heartbeat-Tracking
    }

    private fun handleUnknown(opcode: Int, frame: ByteArray) {
        Log.d(TAG, "ControlFrame: UNKNOWN opcode=$opcode len=${frame.size}")
    }
}

    // BACKEND-53: Auto-PONG bei PING
    private fun handlePing(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: PING [ACK→PONG]")

        // PONG bauen & über Transport senden
        val pong = ControlFrameBuilder.pong()
        com.securecall.app.ghostnet.transport.GhostTransport.get().sendControlFrame(pong)
    }

    // BACKEND-53: Trigger RTT-Berechnung
    private fun handlePong(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: PONG [ACK received]")
        com.securecall.app.ghostnet.transport.GhostTransport.get().handlePongAck()
    }

    // BACKEND-54: KeepAliveEngine informieren
    private fun handlePong(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: PONG [ACK received]")
        com.securecall.app.ghostnet.session.keepalive.KeepAliveEngine.updatePongReceived()
        com.securecall.app.ghostnet.transport.GhostTransport.get().handlePongAck()
    }

    // BACKEND-54: KeepAliveEngine informieren
    private fun handlePong(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: PONG [ACK received]")
        com.securecall.app.ghostnet.session.keepalive.KeepAliveEngine.updatePongReceived()
        com.securecall.app.ghostnet.transport.GhostTransport.get().handlePongAck()
    }

    // BACKEND-62: zentraler Entry-Point für Control-Frames
    fun parse(frame: ByteArray) {
        if (frame.isEmpty()) {
            android.util.Log.w(TAG, "parse(): empty frame — ignoring")
            return
        }

        // MVP-Header-Byte
        val header = frame[0].toInt() and 0xFF

        when (header) {

            0x01 -> { // PING
                android.util.Log.d(TAG, "parse(): PING received")
                handlePing(frame)
            }

            0x02 -> { // PONG
                android.util.Log.d(TAG, "parse(): PONG received")
                handlePong(frame)
            }

            else -> {
                android.util.Log.w(TAG, "parse(): unknown control header: $header")
            }
        }
    }

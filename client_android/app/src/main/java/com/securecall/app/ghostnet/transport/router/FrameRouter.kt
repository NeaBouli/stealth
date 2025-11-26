package com.securecall.app.ghostnet.transport.router

import android.util.Log

// BACKEND-44 / ANDROID-03:
// FrameRouter — spätere zentrale Stelle zur Verarbeitung eingehender Frames.
object FrameRouter {

    private const val TAG = "FRAME_ROUTER"

    // Zukunft: Typen erkennen
    // 0x01 = Audio Frame
    // 0x02 = Control Frame
    // 0x03 = Handshake Frame
    fun route(frame: ByteArray) {
        if (frame.isEmpty()) {
            Log.w(TAG, "Empty frame received — ignoring")
            return
        }

        val type = frame[0].toInt() and 0xFF

        when (type) {
            0x01 -> routeAudio(frame)
            0x02 -> routeControl(frame)
            0x03 -> routeHandshake(frame)
            else -> routeUnknown(frame)
        }
    }

    private fun routeAudio(frame: ByteArray) {
        Log.d(TAG, "AudioFrame received (len=${frame.size}) — [placeholder]")
        // später: AudioDecoder → AudioOutput
    }

    private fun routeControl(frame: ByteArray) {
        Log.d(TAG, "ControlFrame received — [placeholder]")
        // später: State-Machine
    }

    private fun routeHandshake(frame: ByteArray) {
        Log.d(TAG, "HandshakeFrame received — [placeholder]")
        // später: Secure Handshake
    }

    private fun routeUnknown(frame: ByteArray) {
        Log.d(TAG, "UnknownFrame type=${frame[0]} len=${frame.size}")
    }
}

    // BACKEND-45: AudioDecoder-Anbindung
    private fun routeAudio(frame: ByteArray) {
        Log.d(TAG, "AudioFrame received (len=${frame.size}) → forwarding to AudioDecoder")
        com.securecall.app.audio.decode.AudioDecoder.decode(frame)
    }

    // BACKEND-50: ControlFrameParser integrieren
    private fun routeControl(frame: ByteArray) {
        Log.d(TAG, "ControlFrame received → forwarding to ControlFrameParser")
        com.securecall.app.ghostnet.control.ControlFrameParser.parse(frame)
    }

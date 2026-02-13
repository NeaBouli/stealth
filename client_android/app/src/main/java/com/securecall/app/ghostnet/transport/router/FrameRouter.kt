package com.securecall.app.ghostnet.transport.router

import android.util.Log

/**
 * BACKEND-44:
 * Transport-level FrameRouter.
 */
object FrameRouter {

    private const val TAG = "FRAME_ROUTER"

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
        Log.d(TAG, "AudioFrame received (len=${frame.size}) → forwarding to AudioDecoder")
        com.securecall.app.audio.decode.AudioDecoder.decode(frame)
    }

    private fun routeControl(frame: ByteArray) {
        Log.d(TAG, "ControlFrame received → forwarding to ControlFrameParser")
        com.securecall.app.ghostnet.control.ControlFrameParser.parse(frame)
    }

    private fun routeHandshake(frame: ByteArray) {
        Log.d(TAG, "HandshakeFrame received — [placeholder]")
    }

    private fun routeUnknown(frame: ByteArray) {
        Log.d(TAG, "UnknownFrame type=${frame[0]} len=${frame.size}")
    }
}

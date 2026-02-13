package com.securecall.app.ghostnet.control

import android.util.Log

/**
 * BACKEND-49 / BACKEND-62:
 * Parser für Control Frames.
 */
object ControlFrameParser {

    private const val TAG = "CONTROL_FRAME"

    fun parse(frame: ByteArray) {
        if (frame.isEmpty()) {
            Log.w(TAG, "parse(): empty frame — ignoring")
            return
        }

        val header = frame[0].toInt() and 0xFF

        when (header) {
            0x01 -> {
                Log.d(TAG, "parse(): PING received")
                handlePing(frame)
            }
            0x02 -> {
                Log.d(TAG, "parse(): PONG received")
                handlePong(frame)
            }
            0x03 -> handleMute(frame)
            0x04 -> handleUnmute(frame)
            else -> handleUnknown(header, frame)
        }
    }

    private fun handleMute(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: MUTE [placeholder]")
    }

    private fun handleUnmute(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: UNMUTE [placeholder]")
    }

    private fun handlePing(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: PING [ACK→PONG]")
        val pong = ControlFrameBuilder.pong()
        // later: send pong via transport
    }

    private fun handlePong(frame: ByteArray) {
        Log.d(TAG, "ControlFrame: PONG [ACK received]")
    }

    private fun handleUnknown(opcode: Int, frame: ByteArray) {
        Log.d(TAG, "ControlFrame: UNKNOWN opcode=$opcode len=${frame.size}")
    }
}

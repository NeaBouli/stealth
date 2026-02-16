package com.securecall.app.ghostnet.control

import android.util.Log

/**
 * BACKEND-59/61:
 * Zentraler Router für Control- und Media-Frames.
 */
object GhostControlRouter {

    private const val TAG = "GHOST_CTRL_ROUTER"

    fun rebind() {
        Log.w(TAG, "Rebinding GhostControlRouter → new Transport/Parser instance")
    }

    fun routeIncoming(frame: ByteArray) {
        val size = frame.size
        Log.d(TAG, "routeIncoming() called, size=$size")

        if (isControlFrame(frame)) {
            Log.d(TAG, "routeIncoming() → CONTROL → parse()")
            dispatchControl(frame)
        } else {
            Log.d(TAG, "routeIncoming() → MEDIA → dispatchMedia()")
            dispatchMedia(frame)
        }
    }

    private fun isControlFrame(frame: ByteArray): Boolean {
        if (frame.size < 4) return false
        // Parse FrameHeaderV1 — control frames have CONTROL flag set
        val header = com.securecall.app.ghostnet.frame.header.FrameHeaderV1.parse(frame)
        return header != null &&
            header.flags == com.securecall.app.ghostnet.frame.header.FrameFlags.CONTROL
    }

    private fun dispatchControl(frame: ByteArray) {
        try {
            ControlFrameParser.parse(frame)
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchControl() failed", t)
        }
    }

    private fun dispatchMedia(frame: ByteArray) {
        try {
            val media = com.securecall.app.ghostnet.media.MediaFrame(frame)
            com.securecall.app.ghostnet.media.GhostMediaRouter.route(media)
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchMedia() failed", t)
        }
    }
}

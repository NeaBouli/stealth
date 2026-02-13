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
        return frame.isNotEmpty()
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

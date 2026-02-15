package com.securecall.app.ghostnet.transport.debug

import android.util.Log

object IncomingFrameLogger {
    fun logFrame(frame: ByteArray) {
        Log.d("FRAME_LOG", "Incoming frame: size=${frame.size}")
    }
}

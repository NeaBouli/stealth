package com.securecall.app.ghostnet.media

import android.util.Log

/**
 * PATCH 239:
 * Dummy Playback Sink. Gibt PCM nur in Logs aus.
 */
object DummyPlaybackSink {

    private const val TAG = "DUMMY_SINK"

    fun play(pcm: ShortArray) {
        Log.d(TAG, "play(): PCM size=${pcm.size}")
        postPlayEvent(pcm.size)
    }

    // PATCH 240: EventBus Meldung für Playback
    private fun postPlayEvent(size: Int) {
        com.securecall.app.debug.GhostDebugEventBus.post(
            "SINK",
            "play() called with pcmSize=$size"
        )
    }
}

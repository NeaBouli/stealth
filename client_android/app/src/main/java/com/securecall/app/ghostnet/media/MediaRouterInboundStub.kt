package com.securecall.app.ghostnet.media

import android.util.Log

/**
 * AUDIO-11 / ROUTER-20:
 * Stub entry point for inbound audio in the media router.
 *
 * Later this will be called from GhostMediaRouter once a frame
 * has been fully decrypted and decoded to PCM.
 */
object MediaRouterInboundStub {

    private const val TAG = "MEDIA_ROUTER_INBOUND"

    /**
     * Handle a decoded PCM frame from the inbound pipeline.
     *
     * For now we just forward to AudioPlaybackStub and log.
     */
    fun handleDecodedPcm(pcm: ByteArray) {
        Log.d(TAG, "handleDecodedPcm(): got ${pcm.size} bytes of PCM, forwarding to AudioPlaybackStub")
        AudioPlaybackStub.enqueuePcm(pcm)
    }
}

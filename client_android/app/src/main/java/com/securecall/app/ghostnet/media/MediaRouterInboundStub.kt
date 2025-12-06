package com.securecall.app.ghostnet.media

import android.util.Log

/**
 * Minimal inbound media router:
 * - receives decoded PCM frames,
 * - forwards them to AudioPlaybackStub.
 */
object MediaRouterInboundStub {

    private const val TAG = "MEDIA_ROUTER_INBOUND"

    @JvmStatic
    fun handleDecodedPcm(pcm: ByteArray) {
        Log.d(TAG, "handleDecodedPcm(): got \${pcm.size} bytes of PCM, forwarding to AudioPlaybackStub")
        AudioPlaybackStub.enqueuePcm(pcm)
    }
}

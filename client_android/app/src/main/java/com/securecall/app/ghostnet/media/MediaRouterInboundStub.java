package com.securecall.app.ghostnet.media;

import android.util.Log;

/**
 * MEDIA_ROUTER_INBOUND
 *
 * Minimal inbound media router:
 *  - receives decoded PCM frames,
 *  - forwards them to AudioPlaybackStub.
 */
public class MediaRouterInboundStub {

    private static final String TAG = "MEDIA_ROUTER_INBOUND";

    public static void handleDecodedPcm(byte[] pcm) {
        if (pcm == null) {
            return;
        }
        Log.d(TAG, "handleDecodedPcm(): got " + pcm.length + " bytes of PCM, forwarding to AudioPlaybackStub");
        AudioPlaybackStub.enqueuePcm(pcm);
    }
}

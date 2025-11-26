package com.securecall.app.ghostnet;

import android.util.Log;

import com.securecall.app.ghostnet.frames.AudioFrame;

public class AudioPipeline {

    private static final String TAG = "AudioPipeline";

    private GhostNetTransport transport;

    public void attachTransport(GhostNetTransport t) {
        this.transport = t;
        Log.d(TAG, "Transport attached to AudioPipeline");
    }

    public void start() {
        Log.d(TAG, "AudioPipeline started (MVP)");

        if (transport != null) {
            // ANDROID-08: Dummy test frame
            byte[] dummy = new byte[] { 1, 2, 3, 4 };
            AudioFrame frame = new AudioFrame(dummy);
            transport.sendAudioFrame(frame);

            Log.d(TAG, "Dummy AudioFrame sent via GhostNetTransport (MVP)");
        }
    }

    public void stop() {
        Log.d(TAG, "AudioPipeline stopped (MVP)");
    }
}

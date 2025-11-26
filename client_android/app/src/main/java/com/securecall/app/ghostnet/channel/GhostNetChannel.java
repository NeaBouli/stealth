package com.securecall.app.ghostnet.channel;

import android.util.Log;

/**
 * ANDROID-11
 * GhostNetChannel (MVP) – zukünftiger Transport-Layer.
 *
 * Placeholder für:
 *  - QUIC Client
 *  - WebRTC DataChannel
 *  - PeerConnection
 *  - ICE Handling
 *  - Secure Frame Transmission
 */
public class GhostNetChannel {

    private static final String TAG = "GhostNetChannel";

    public enum ChannelState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private ChannelState state = ChannelState.DISCONNECTED;

    public GhostNetChannel() {
        Log.d(TAG, "GhostNetChannel created (MVP skeleton)");
    }

    public void connect(String remotePeerId) {
        Log.d(TAG, "connect() called for peer=" + remotePeerId);
        state = ChannelState.CONNECTING;

        // MVP: keine Netzwerklogik
        Log.d(TAG, "Pretending to establish QUIC/WebRTC channel...");
        state = ChannelState.CONNECTED;

        Log.d(TAG, "GhostNetChannel state=" + state);
    }

    public void disconnect() {
        Log.d(TAG, "disconnect() called");
        state = ChannelState.DISCONNECTED;
        Log.d(TAG, "GhostNetChannel state=" + state);
    }

    public ChannelState getState() {
        return state;
    }

    public void sendBytes(byte[] data) {
        Log.d(TAG, "sendBytes(len=" + data.length + ") [MVP placeholder]");
        // no network logic here yet
    }
}

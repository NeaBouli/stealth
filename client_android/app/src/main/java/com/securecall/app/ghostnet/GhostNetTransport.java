package com.securecall.app.ghostnet;

import android.util.Log;

import com.securecall.app.ghostnet.frames.AudioFrame;
import com.securecall.app.ghostnet.frames.ControlFrame;
import com.securecall.app.ghostnet.frames.GhostFrame;
import com.securecall.app.ghostnet.queue.OutgoingFrameQueue;
import com.securecall.app.ghostnet.scheduler.GhostNetScheduler;
import com.securecall.app.ghostnet.channel.GhostNetChannel;

public class GhostNetTransport {

    private static final String TAG = "GhostNetTransport";

    private final GhostNetSession session;
    private final OutgoingFrameQueue outgoing;
    private GhostNetScheduler scheduler;
    private GhostNetChannel channel;

    public GhostNetTransport() {
        this.session = new GhostNetSession();
        this.outgoing = new OutgoingFrameQueue();
        this.channel = new GhostNetChannel();

        Log.d(TAG, "GhostNetTransport initialized");
        Log.d(TAG, "Local Session ID: " + session.getSessionId());
    }

    public void start() {
        Log.d(TAG, "GhostNetTransport starting...");
        session.activate();
        Log.d(TAG, "Session active: " + session.isActive());

        // ANDROID-11: Channel connect (MVP)
        if (session.getRemotePeerId() != null) {
            connectChannel();
        } else {
            Log.d(TAG, "Channel connect skipped (no remote peer set)");
        }

        // ANDROID-10: Scheduler
        scheduler = new GhostNetScheduler(this);
        scheduler.start();
    }

    public void stop() {
        Log.d(TAG, "GhostNetTransport stopping...");
        session.deactivate();
        Log.d(TAG, "Session active: " + session.isActive());

        // ANDROID-11: Channel disconnect
        disconnectChannel();

        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    // -----------------------------
    // Channel Management (ANDROID-11)
    // -----------------------------
    private void connectChannel() {
        Log.d(TAG, "Connecting GhostNetChannel (MVP)...");
        channel.connect(session.getRemotePeerId());
    }

    private void disconnectChannel() {
        Log.d(TAG, "Disconnecting GhostNetChannel (MVP)...");
        channel.disconnect();
    }

    public boolean channelConnected() {
        return channel.getState() == GhostNetChannel.ChannelState.CONNECTED;
    }

    // -----------------------------
    // Peer Management (ANDROID-07)
    // -----------------------------
    public void setRemotePeer(String peerId) {
        Log.d(TAG, "Remote Peer set: " + peerId);
        session.setRemotePeerId(peerId);
    }

    public String getRemotePeer() {
        return session.getRemotePeerId();
    }

    public String getLocalSessionId() {
        return session.getSessionId();
    }

    // -----------------------------
    // Frame + Queue System (A08/A09)
    // -----------------------------
    public void sendAudioFrame(AudioFrame frame) {
        GhostFrame gf = frame.toGhostFrame();
        outgoing.add(gf);
        Log.d(TAG, "[QUEUE] AudioFrame queued. size=" + outgoing.size());
    }

    public void sendControlFrame(ControlFrame frame) {
        GhostFrame gf = frame.toGhostFrame();
        outgoing.add(gf);
        Log.d(TAG, "[QUEUE] ControlFrame queued. type=" + frame.getType() +
                " size=" + outgoing.size());
    }

    public void processQueue() {
        if (outgoing.isEmpty()) {
            Log.d(TAG, "[QUEUE] Nothing to process");
            return;
        }

        GhostFrame next = outgoing.poll();
        Log.d(TAG, "[QUEUE] Processing frame: type=" + next.getType());

        // ANDROID-11: still no channel send logic (MVP)
        if (!channelConnected()) {
            Log.d(TAG, "[QUEUE] Channel not connected (MVP, skipping send)");
            return;
        }

        Log.d(TAG, "[CHANNEL] (MVP) would send " + next.getPayload().length + " bytes");
    }

    public void onIncomingFrame(GhostFrame frame) {
        switch (frame.getType()) {
            case AUDIO:
                Log.d(TAG, "[RECV] AudioFrame (" + frame.getPayload().length + ")");
                break;
            case CONTROL:
                Log.d(TAG, "[RECV] ControlFrame: " + new String(frame.getPayload()));
                break;
        }
    }
}

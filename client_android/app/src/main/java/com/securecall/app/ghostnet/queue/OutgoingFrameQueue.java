package com.securecall.app.ghostnet.queue;

import android.util.Log;
import com.securecall.app.ghostnet.frames.GhostFrame;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OutgoingFrameQueue {

    private static final String TAG = "OutgoingFrameQueue";

    private final Queue<GhostFrame> queue = new ConcurrentLinkedQueue<>();

    public void add(GhostFrame frame) {
        queue.add(frame);
        Log.d(TAG, "Frame queued: type=" + frame.getType() + " size=" + frame.getPayload().length);
    }

    public GhostFrame poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }
}

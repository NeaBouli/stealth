package com.securecall.app.ghostnet.scheduler;

import android.util.Log;

import com.securecall.app.ghostnet.GhostNetTransport;

public class GhostNetScheduler extends Thread {

    private static final String TAG = "GhostNetScheduler";

    private final GhostNetTransport transport;
    private boolean running = true;

    public GhostNetScheduler(GhostNetTransport transport) {
        this.transport = transport;
        Log.d(TAG, "Scheduler created.");
    }

    @Override
    public void run() {
        Log.d(TAG, "Scheduler started.");

        while (running) {
            try {
                // MVP: artificial tick
                Thread.sleep(500);

                if (transport != null) {
                    transport.processQueue();
                }

            } catch (InterruptedException e) {
                Log.e(TAG, "Scheduler interrupted: " + e.getMessage());
                running = false;
            }
        }

        Log.d(TAG, "Scheduler stopped.");
    }

    public void shutdown() {
        Log.d(TAG, "Shutdown request.");
        running = false;
        this.interrupt();
    }
}

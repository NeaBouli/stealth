package com.securecall.app.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;

public class GhostVpnService extends VpnService {

    private static final String TAG = "GhostVPN";
    private ParcelFileDescriptor vpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "Starting GhostVPN (MVP)...");

        if (vpnInterface != null) {
            Log.d(TAG, "GhostVPN already active.");
            return START_STICKY;
        }

        try {
            Builder builder = new Builder();
            builder.setSession("GhostVPN-MVP");

            // MVP: Dummy interface, no routing
            builder.addAddress("10.0.0.2", 32);
            builder.addDnsServer("1.1.1.1");

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                Log.d(TAG, "GhostVPN established successfully.");
            } else {
                Log.e(TAG, "Failed to establish GhostVPN interface.");
            }

        } catch (Exception e) {
            Log.e(TAG, "GhostVPN error: " + e.getMessage(), e);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Stopping GhostVPN...");
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing GhostVPN interface: " + e.getMessage(), e);
        }
        super.onDestroy();
    }
}

package com.securecall.app.vpn;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class VpnController {

    private static final String TAG = "VpnController";

    public static void start(Context ctx) {
        Log.d(TAG, "Request to start GhostVPN.");
        Intent i = new Intent(ctx, GhostVpnService.class);
        ctx.startService(i);
    }

    public static void stop(Context ctx) {
        Log.d(TAG, "Request to stop GhostVPN.");
        Intent i = new Intent(ctx, GhostVpnService.class);
        ctx.stopService(i);
    }
}

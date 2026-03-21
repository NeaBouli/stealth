package com.securecall.app.vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.util.Log;

/**
 * Controls the StealthX VPN service lifecycle and configuration.
 * Premium feature: WireGuard-compatible split tunnel VPN.
 */
public class VpnController {

    private static final String TAG = "VpnController";
    private static final String PREFS = "securecall_prefs";
    public static final int VPN_PERMISSION_REQUEST = 3001;

    /** Request VPN permission from the user. Returns true if permission already granted. */
    public static boolean requestPermission(Activity activity) {
        Intent intent = VpnService.prepare(activity);
        if (intent != null) {
            activity.startActivityForResult(intent, VPN_PERMISSION_REQUEST);
            return false;
        }
        return true; // Already has permission
    }

    /** Start the VPN service. Must have permission first. */
    public static void start(Context ctx) {
        Log.d(TAG, "Starting GhostVPN service");
        Intent i = new Intent(ctx, GhostVpnService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    /** Stop the VPN service. */
    public static void stop(Context ctx) {
        Log.d(TAG, "Stopping GhostVPN service");
        Intent i = new Intent(ctx, GhostVpnService.class);
        i.setAction("STOP");
        ctx.startService(i);
    }

    /** Check if VPN is currently active. */
    public static boolean isActive() {
        return GhostVpnService.isActive;
    }

    /** Get connected server info. */
    public static String getConnectedServer() {
        return GhostVpnService.connectedServer;
    }

    /** Check if WireGuard config is saved. */
    public static boolean hasConfig(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String endpoint = prefs.getString("vpn_server_endpoint", "");
        return endpoint != null && !endpoint.isEmpty();
    }

    /** Save WireGuard configuration. */
    public static void saveConfig(Context ctx, String endpoint, int port,
                                   String serverPubKey, String clientPrivKey,
                                   String dns, String allowedIps, String clientAddress,
                                   boolean killSwitch) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("vpn_server_endpoint", endpoint)
            .putInt("vpn_server_port", port)
            .putString("vpn_server_public_key", serverPubKey)
            .putString("vpn_client_private_key", clientPrivKey)
            .putString("vpn_dns", dns)
            .putString("vpn_allowed_ips", allowedIps)
            .putString("vpn_client_address", clientAddress)
            .putBoolean("vpn_kill_switch", killSwitch)
            .apply();
        Log.d(TAG, "VPN config saved: " + endpoint + ":" + port);
    }

    /** Clear VPN configuration. */
    public static void clearConfig(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("vpn_server_endpoint")
            .remove("vpn_server_port")
            .remove("vpn_server_public_key")
            .remove("vpn_client_private_key")
            .remove("vpn_dns")
            .remove("vpn_allowed_ips")
            .remove("vpn_client_address")
            .remove("vpn_kill_switch")
            .remove("vpn_enabled")
            .apply();
        Log.d(TAG, "VPN config cleared");
    }

    /** Check if VPN is enabled in settings. */
    public static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("vpn_enabled", false);
    }

    /** Set VPN enabled state. */
    public static void setEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("vpn_enabled", enabled).apply();
    }
}

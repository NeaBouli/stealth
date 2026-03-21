package com.securecall.app.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.securecall.app.R;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.InetEndpoint;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;

/**
 * StealthX VPN Service — Real WireGuard tunnel via wireguard-android GoBackend.
 * Full Noise_IKpsk2 handshake, ChaCha20-Poly1305 packet encryption.
 * Split tunnel: only SecureCall app traffic routed through VPN.
 * Premium feature only.
 */
public class GhostVpnService extends VpnService {

    private static final String TAG = "GhostVPN";
    private static final String CHANNEL_ID = "securecall_vpn";
    private static final int NOTIFICATION_ID = 2001;

    private GoBackend backend;
    private GhostTunnel tunnel;

    public static volatile boolean isActive = false;
    public static volatile String connectedServer = null;

    @Override
    public void onCreate() {
        super.onCreate();
        backend = new GoBackend(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Starting GhostVPN...");

        if (isActive && tunnel != null) {
            Log.d(TAG, "GhostVPN already active.");
            return START_STICKY;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."));

        new Thread(() -> {
            try {
                Config config = buildWireGuardConfig();
                if (config == null) {
                    Log.w(TAG, "No valid WireGuard config — cannot start VPN");
                    updateNotification("No configuration");
                    stopSelf();
                    return;
                }

                tunnel = new GhostTunnel();
                backend.setState(tunnel, Tunnel.State.UP, config);

                String endpoint = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
                        .getString("vpn_server_endpoint", "unknown");
                isActive = true;
                connectedServer = endpoint;
                updateNotification("Connected to " + endpoint);
                Log.d(TAG, "WireGuard tunnel UP — Noise handshake complete, connected to " + endpoint);

            } catch (Exception e) {
                Log.e(TAG, "WireGuard tunnel failed: " + e.getMessage(), e);
                isActive = false;
                connectedServer = null;
                updateNotification("Failed: " + e.getMessage());
            }
        }, "ghost-vpn-start").start();

        return START_STICKY;
    }

    private Config buildWireGuardConfig() {
        SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
        String endpoint = prefs.getString("vpn_server_endpoint", "");
        int port = prefs.getInt("vpn_server_port", 51820);
        String serverPubKey = prefs.getString("vpn_server_public_key", "");
        String clientPrivKey = prefs.getString("vpn_client_private_key", "");
        String dns = prefs.getString("vpn_dns", "1.1.1.1");
        String allowedIps = prefs.getString("vpn_allowed_ips", "0.0.0.0/0");
        String clientAddress = prefs.getString("vpn_client_address", "10.66.66.2/32");

        if (endpoint.isEmpty() || serverPubKey.isEmpty() || clientPrivKey.isEmpty()) {
            Log.w(TAG, "Incomplete WireGuard config");
            return null;
        }

        try {
            Interface.Builder ifaceBuilder = new Interface.Builder();
            ifaceBuilder.parsePrivateKey(clientPrivKey);
            ifaceBuilder.addAddress(InetNetwork.parse(clientAddress));
            for (String d : dns.split(",")) {
                String trimmed = d.trim();
                if (!trimmed.isEmpty()) {
                    ifaceBuilder.addDnsServer(java.net.InetAddress.getByName(trimmed));
                }
            }
            ifaceBuilder.includeApplication(getPackageName());

            Peer.Builder peerBuilder = new Peer.Builder();
            peerBuilder.parsePublicKey(serverPubKey);
            peerBuilder.setEndpoint(InetEndpoint.parse(endpoint + ":" + port));
            for (String ip : allowedIps.split(",")) {
                String trimmed = ip.trim();
                if (!trimmed.isEmpty()) {
                    peerBuilder.addAllowedIp(InetNetwork.parse(trimmed));
                }
            }
            peerBuilder.setPersistentKeepalive(25);

            Config.Builder configBuilder = new Config.Builder();
            configBuilder.setInterface(ifaceBuilder.build());
            configBuilder.addPeer(peerBuilder.build());

            Log.d(TAG, "WireGuard config: " + endpoint + ":" + port);
            return configBuilder.build();
        } catch (Exception e) {
            Log.e(TAG, "Config build failed: " + e.getMessage(), e);
            return null;
        }
    }

    private void stopVpn() {
        Log.d(TAG, "Stopping GhostVPN...");
        isActive = false;
        connectedServer = null;
        if (backend != null && tunnel != null) {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
                Log.d(TAG, "WireGuard tunnel DOWN");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping tunnel", e);
            }
        }
        tunnel = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() { stopVpn(); super.onDestroy(); }

    @Override public void onRevoke() {
        Log.w(TAG, "VPN permission revoked");
        stopVpn();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String status) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("StealthX VPN").setContentText(status)
                .setSmallIcon(R.drawable.ic_lock).setOngoing(true).setSilent(true).build();
    }

    private void updateNotification(String status) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(status));
    }

    private static class GhostTunnel implements Tunnel {
        @Override public String getName() { return "stealthx"; }
        @Override public void onStateChange(State s) {
            Log.d("GhostVPN", "Tunnel state: " + s);
            isActive = (s == State.UP);
            if (s != State.UP) connectedServer = null;
        }
    }
}

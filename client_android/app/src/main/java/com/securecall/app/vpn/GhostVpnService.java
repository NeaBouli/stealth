package com.securecall.app.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * StealthX VPN Service — Real WireGuard tunnel via wireguard-android GoBackend.
 * Full Noise_IKpsk2 handshake, ChaCha20-Poly1305 packet encryption.
 * Split tunnel: only SecureCall app traffic routed through VPN.
 * Premium feature only.
 *
 * Modes:
 *   MODE_WIREGUARD         — standard WireGuard (default network as underlay)
 *   MODE_WIREGUARD_VIA_ESIM — WireGuard tunnel with eSIM/cellular as physical underlay
 */
public class GhostVpnService extends VpnService {

    private static final String TAG = "GhostVPN";
    private static final String CHANNEL_ID = "securecall_vpn";
    private static final int NOTIFICATION_ID = 2001;

    public static final String MODE_WIREGUARD = "WIREGUARD";
    public static final String MODE_WIREGUARD_VIA_ESIM = "WIREGUARD_VIA_ESIM";
    public static final String EXTRA_MODE = "vpn_mode";

    private GoBackend backend;
    private GhostTunnel tunnel;

    public static volatile boolean isActive = false;
    public static volatile String connectedServer = null;
    public static volatile String currentMode = MODE_WIREGUARD;

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

        String requestedMode = (intent != null)
                ? intent.getStringExtra(EXTRA_MODE)
                : null;
        currentMode = (requestedMode != null) ? requestedMode : MODE_WIREGUARD;
        Log.d(TAG, "Starting GhostVPN in mode: " + currentMode);

        if (isActive && tunnel != null) {
            Log.d(TAG, "GhostVPN already active (mode=" + currentMode + ")");
            return START_STICKY;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."));

        final String mode = currentMode;
        new Thread(() -> {
            try {
                // WIREGUARD_VIA_ESIM: bind process to cellular BEFORE GoBackend creates its socket
                // so GoBackend's protected WireGuard socket goes out via eSIM underlay.
                Network cellularNetwork = null;
                if (MODE_WIREGUARD_VIA_ESIM.equals(mode)) {
                    cellularNetwork = findCellularNetwork();
                    if (cellularNetwork != null) {
                        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                        cm.bindProcessToNetwork(cellularNetwork);
                        Log.d(TAG, "ESIM: bound process to cellular network for WireGuard socket");
                    } else {
                        Log.w(TAG, "ESIM: no cellular network found — falling back to default underlay");
                    }
                }

                Config config = buildWireGuardConfig();
                if (config == null) {
                    Log.w(TAG, "No valid WireGuard config — cannot start VPN");
                    releaseCellularBinding(mode);
                    updateNotification("No configuration");
                    stopSelf();
                    return;
                }

                tunnel = new GhostTunnel();
                backend.setState(tunnel, Tunnel.State.UP, config);

                // Release process binding — VPN tunnel now handles routing.
                // GoBackend's socket stays eSIM-bound (sockets bind at creation time).
                releaseCellularBinding(mode);

                // Inform Android of the physical underlay network
                if (MODE_WIREGUARD_VIA_ESIM.equals(mode) && cellularNetwork != null) {
                    setUnderlyingNetworks(new Network[]{cellularNetwork});
                    Log.d(TAG, "ESIM: underlay set to cellular network");
                }

                String endpoint = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
                        .getString("vpn_server_endpoint", "unknown");
                isActive = true;
                connectedServer = endpoint;
                String modeLabel = MODE_WIREGUARD_VIA_ESIM.equals(mode) ? " [eSIM underlay]" : "";
                updateNotification("Connected to " + endpoint + modeLabel);
                Log.d(TAG, "WireGuard tunnel UP — connected to " + endpoint + modeLabel);

            } catch (Exception e) {
                Log.e(TAG, "WireGuard tunnel failed: " + e.getMessage(), e);
                releaseCellularBinding(mode);
                isActive = false;
                connectedServer = null;
                updateNotification("Failed: " + e.getMessage());
            }
        }, "ghost-vpn-start").start();

        return START_STICKY;
    }

    /**
     * Finds an available cellular (eSIM/mobile data) network synchronously.
     * Returns null if no cellular network is available within 3 seconds.
     */
    private Network findCellularNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return null;

        // Check if a cellular network is already active
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return activeNetwork;
            }
        }

        // Request cellular network synchronously with a short timeout
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        final Network[] found = {null};
        CountDownLatch latch = new CountDownLatch(1);
        ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                found[0] = network;
                latch.countDown();
            }
        };

        try {
            cm.requestNetwork(request, cb);
            boolean got = latch.await(3, TimeUnit.SECONDS);
            cm.unregisterNetworkCallback(cb);
            if (got) return found[0];
        } catch (Exception e) {
            Log.w(TAG, "findCellularNetwork: " + e.getMessage());
            try { cm.unregisterNetworkCallback(cb); } catch (Exception ignored) {}
        }
        return null;
    }

    private void releaseCellularBinding(String mode) {
        if (MODE_WIREGUARD_VIA_ESIM.equals(mode)) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.bindProcessToNetwork(null);
                Log.d(TAG, "ESIM: released process network binding");
            }
        }
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

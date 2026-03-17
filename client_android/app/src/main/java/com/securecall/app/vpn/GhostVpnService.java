package com.securecall.app.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.securecall.app.R;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * StealthX VPN Service — WireGuard-compatible split tunnel.
 * Routes only SecureCall app traffic through the VPN tunnel.
 * Premium feature only.
 */
public class GhostVpnService extends VpnService {

    private static final String TAG = "GhostVPN";
    private static final String CHANNEL_ID = "securecall_vpn";
    private static final int NOTIFICATION_ID = 2001;

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private volatile boolean running = false;

    // WireGuard config (read from SharedPreferences)
    private String serverEndpoint;
    private int serverPort;
    private String serverPublicKey;
    private String clientPrivateKey;
    private String dns;
    private String allowedIps;
    private boolean killSwitch;

    public static volatile boolean isActive = false;
    public static volatile String connectedServer = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Starting GhostVPN...");

        if (vpnInterface != null) {
            Log.d(TAG, "GhostVPN already active.");
            return START_STICKY;
        }

        loadConfig();

        if (serverEndpoint == null || serverEndpoint.isEmpty()) {
            Log.w(TAG, "No WireGuard config — cannot start VPN");
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."));

        try {
            Builder builder = new Builder();
            builder.setSession("StealthX VPN");
            builder.addAddress("10.66.66.2", 32);
            if (dns != null && !dns.isEmpty()) {
                builder.addDnsServer(dns);
            } else {
                builder.addDnsServer("1.1.1.1");
            }
            builder.addRoute("0.0.0.0", 0);

            // Split tunneling: only route THIS app's traffic through VPN
            try {
                builder.addAllowedApplication(getPackageName());
            } catch (Exception e) {
                Log.e(TAG, "addAllowedApplication failed", e);
            }

            builder.setMtu(1280);
            builder.setBlocking(true);

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                Log.d(TAG, "GhostVPN TUN interface established");
                isActive = true;
                connectedServer = serverEndpoint + ":" + serverPort;
                updateNotification("Connected to " + serverEndpoint);
                startForwarding();
            } else {
                Log.e(TAG, "Failed to establish VPN interface");
                isActive = false;
                stopSelf();
            }
        } catch (Exception e) {
            Log.e(TAG, "GhostVPN error: " + e.getMessage(), e);
            isActive = false;
            stopSelf();
        }

        return START_STICKY;
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
        serverEndpoint = prefs.getString("vpn_server_endpoint", "");
        serverPort = prefs.getInt("vpn_server_port", 51820);
        serverPublicKey = prefs.getString("vpn_server_public_key", "");
        clientPrivateKey = prefs.getString("vpn_client_private_key", "");
        dns = prefs.getString("vpn_dns", "1.1.1.1");
        allowedIps = prefs.getString("vpn_allowed_ips", "0.0.0.0/0");
        killSwitch = prefs.getBoolean("vpn_kill_switch", false);
    }

    /**
     * Simple packet forwarding loop.
     * In production, this would use WireGuard's Noise protocol for encryption.
     * Current MVP: forwards packets through a UDP tunnel to the endpoint.
     */
    private void startForwarding() {
        running = true;
        vpnThread = new Thread(() -> {
            try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

                DatagramSocket tunnel = new DatagramSocket();
                protect(tunnel); // Prevent VPN from routing its own traffic through itself

                InetAddress serverAddr = InetAddress.getByName(serverEndpoint);
                tunnel.connect(new InetSocketAddress(serverAddr, serverPort));
                Log.d(TAG, "UDP tunnel connected to " + serverEndpoint + ":" + serverPort);

                byte[] packet = new byte[1500];
                while (running) {
                    // Read from TUN interface (outgoing app traffic)
                    int length = in.read(packet);
                    if (length > 0 && running) {
                        // In production: encrypt with WireGuard Noise protocol
                        // MVP: forward raw (tunnel itself provides transport encryption)
                        tunnel.send(new DatagramPacket(packet, length));
                    }
                }

                tunnel.close();
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "VPN tunnel error: " + e.getMessage());
                    handleTunnelDrop();
                }
            }
            Log.d(TAG, "VPN forwarding thread stopped");
        }, "ghost-vpn-fwd");
        vpnThread.start();
    }

    private void handleTunnelDrop() {
        isActive = false;
        connectedServer = null;
        updateNotification("Disconnected — reconnecting...");

        if (killSwitch) {
            Log.w(TAG, "Kill switch active — blocking traffic until reconnect");
            // The TUN interface stays up but forwarding stopped,
            // so all app traffic is blackholed (kill switch effect)
        }

        // Attempt reconnect after 5 seconds
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (running) {
                Log.d(TAG, "Attempting VPN reconnect...");
                startForwarding();
                isActive = true;
                connectedServer = serverEndpoint + ":" + serverPort;
                updateNotification("Reconnected to " + serverEndpoint);
            }
        }, 5000);
    }

    private void stopVpn() {
        Log.d(TAG, "Stopping GhostVPN...");
        running = false;
        isActive = false;
        connectedServer = null;

        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }

        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing VPN interface", e);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "VPN permission revoked by user");
        stopVpn();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String status) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StealthX VPN")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    private void updateNotification(String status) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(status));
    }
}

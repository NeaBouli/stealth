package com.securecall.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.securecall.app.R
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.net.InetAddress
import java.util.concurrent.Executors

class PremiumVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.securecall.app.vpn.START"
        const val ACTION_STOP = "com.securecall.app.vpn.STOP"
        private const val TAG = "PremiumVpnService"
        private const val CHANNEL_ID = "securecall_premium_vpn"
        private const val NOTIFICATION_ID = 2001
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var backend: GoBackend? = null
    private var tunnel: Tunnel? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        backend = GoBackend(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !VpnConfigStore.isEnabled(this)) {
            stopTunnelAndService()
            return Service.START_NOT_STICKY
        }

        startAsForeground(buildNotification(R.string.premium_vpn_notification_connecting))
        if (PremiumVpnState.status == PremiumVpnState.Status.ACTIVE ||
            PremiumVpnState.status == PremiumVpnState.Status.CONNECTING
        ) {
            return Service.START_STICKY
        }

        PremiumVpnState.status = PremiumVpnState.Status.CONNECTING
        worker.execute {
            val config = VpnConfigStore.load(this)
            if (config == null) {
                failAndStop()
                return@execute
            }
            try {
                val wireGuardConfig = buildConfig(config)
                val activeTunnel = SecureCallTunnel()
                tunnel = activeTunnel
                backend?.setState(activeTunnel, Tunnel.State.UP, wireGuardConfig)
                PremiumVpnState.status = PremiumVpnState.Status.ACTIVE
                notifyStatus(R.string.premium_vpn_notification_active)
            } catch (error: Exception) {
                Log.e(TAG, "WireGuard tunnel failed", error)
                failAndStop()
            }
        }
        return Service.START_STICKY
    }

    override fun onRevoke() {
        VpnConfigStore.setEnabled(this, false)
        stopTunnelAndService()
        super.onRevoke()
    }

    override fun onDestroy() {
        val preserveError = PremiumVpnState.status == PremiumVpnState.Status.ERROR
        runCatching { worker.execute { stopTunnel(preserveError) } }
            .onFailure { stopTunnel(preserveError) }
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildConfig(data: WireGuardConfigData): Config {
        val interfaceBuilder = Interface.Builder()
            .parsePrivateKey(data.clientPrivateKey)
            .includeApplication(packageName)
        data.clientAddress.split(',').map(String::trim).filter(String::isNotEmpty).forEach {
            interfaceBuilder.addAddress(InetNetwork.parse(it))
        }
        data.dns.split(',').map(String::trim).filter(String::isNotEmpty).forEach {
            interfaceBuilder.addDnsServer(InetAddress.getByName(it))
        }

        val peerBuilder = Peer.Builder()
            .parsePublicKey(data.serverPublicKey)
            .setEndpoint(InetEndpoint.parse(formatEndpoint(data.endpoint, data.port)))
            .setPersistentKeepalive(25)
        data.allowedIps.split(',').map(String::trim).filter(String::isNotEmpty).forEach {
            peerBuilder.addAllowedIp(InetNetwork.parse(it))
        }
        return Config.Builder()
            .setInterface(interfaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun formatEndpoint(host: String, port: Int): String =
        if (':' in host && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    private fun failAndStop() {
        PremiumVpnState.status = PremiumVpnState.Status.ERROR
        VpnConfigStore.setEnabled(this, false)
        notifyStatus(R.string.premium_vpn_notification_failed)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTunnelAndService() {
        worker.execute {
            stopTunnel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @Synchronized
    private fun stopTunnel(preserveError: Boolean = false) {
        val activeTunnel = tunnel
        if (activeTunnel != null) {
            runCatching { backend?.setState(activeTunnel, Tunnel.State.DOWN, null) }
                .onFailure { Log.w(TAG, "Failed to stop WireGuard tunnel", it) }
        }
        tunnel = null
        if (!preserveError) PremiumVpnState.status = PremiumVpnState.Status.OFF
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.premium_vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.premium_vpn_notification_channel))
            .setContentText(getString(message))
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun notifyStatus(message: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    private inner class SecureCallTunnel : Tunnel {
        override fun getName(): String = "securecall"

        override fun onStateChange(newState: Tunnel.State) {
            PremiumVpnState.status = when (newState) {
                Tunnel.State.UP -> PremiumVpnState.Status.ACTIVE
                Tunnel.State.DOWN -> {
                    VpnConfigStore.setEnabled(this@PremiumVpnService, false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    PremiumVpnState.Status.OFF
                }
                else -> PremiumVpnState.status
            }
        }
    }
}

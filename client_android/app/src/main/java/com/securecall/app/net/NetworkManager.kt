package com.securecall.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.CertificatePinner

/**
 * Manages network interface selection for StealthX traffic.
 * Pro/Premium feature: prefer a specific network transport.
 *
 * Default = no binding (Android decides). WiFi/Cellular = explicit bind.
 * When bound network is lost, falls back to default and triggers WS reconnect.
 */
object NetworkManager {
    private const val TAG = "NetworkManager"
    private const val PREFS = "securecall_prefs"
    private const val KEY_PREFERRED_TRANSPORT = "preferred_network_transport"

    const val TRANSPORT_DEFAULT = "default"
    const val TRANSPORT_WIFI = "wifi"
    const val TRANSPORT_CELLULAR = "cellular"

    private var boundNetwork: Network? = null
    private var preferredNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var vpnNetworkCallback: ConnectivityManager.NetworkCallback? = null

    internal fun normalizePreferredTransport(transport: String?): String = when (transport) {
        TRANSPORT_WIFI, TRANSPORT_CELLULAR -> transport
        else -> TRANSPORT_DEFAULT
    }

    internal fun shouldBindPreferredNetwork(
        transport: String?,
        externalVpnActive: Boolean
    ): Boolean = !externalVpnActive && normalizePreferredTransport(transport) != TRANSPORT_DEFAULT

    fun getPreferredTransport(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFERRED_TRANSPORT, TRANSPORT_DEFAULT)
        return normalizePreferredTransport(stored)
    }

    fun setPreferredTransport(context: Context, transport: String) {
        val normalized = normalizePreferredTransport(transport)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PREFERRED_TRANSPORT, normalized).apply()
        Log.d(TAG, "Preferred transport set to: $normalized")
    }

    fun getActiveNetworkInfo(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "No network"
        val caps = cm.getNetworkCapabilities(network) ?: return "Unknown"
        return when {
            // VPN first: when the system VPN tunnels an underlying WiFi/cellular
            // network, capabilities list both transports and VPN is authoritative.
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }
    }

    fun isExternalVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    /**
     * Apply network binding based on preferred transport.
     * Default = no binding (Android decides the best network).
     * WiFi/Cellular = explicit bind via requestNetwork + bindProcessToNetwork.
     */
    fun bindToPreferredNetwork(context: Context) {
        val transport = getPreferredTransport(context)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (transport == TRANSPORT_DEFAULT) {
            unbind(context)
            return
        }

        registerVpnWatcher(context.applicationContext, cm)

        // Never bind around an active device VPN. The system default preserves its routing.
        if (!shouldBindPreferredNetwork(transport, isExternalVpnActive(context))) {
            releasePreferredBinding(cm, reconnect = true)
            return
        }

        releasePreferredBinding(cm, reconnect = false)

        val requestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        when (transport) {
            TRANSPORT_WIFI -> requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            TRANSPORT_CELLULAR -> requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Preferred network available: $transport — binding process")
                boundNetwork = network
                cm.bindProcessToNetwork(network)
                com.securecall.app.debug.SecLogManager.log("NET", "Bound to $transport")
                // Force WebSocket to reconnect on the newly bound network
                // (existing sockets stay on the old network until closed)
                Log.d(TAG, "Triggering WebSocket reconnect on bound network: $transport")
                WebSocketService.instance?.forceReconnect()
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Preferred network lost: $transport — unbinding, falling back to default")
                boundNetwork = null
                cm.bindProcessToNetwork(null) // Release binding → Android uses best available
                com.securecall.app.debug.SecLogManager.log("NET", "Unbound from $transport — fallback to default")
                // Trigger WebSocket reconnect on the new (unbound) network
                WebSocketService.instance?.forceReconnect()
            }
        }
        preferredNetworkCallback = callback

        try {
            cm.requestNetwork(requestBuilder.build(), callback)
            Log.d(TAG, "Requested network binding: $transport")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request network: ${e.message}")
        }
    }

    fun unbind(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        releasePreferredBinding(cm, reconnect = false)
        vpnNetworkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        vpnNetworkCallback = null
        Log.d(TAG, "Network binding released — using system default")
    }

    private fun registerVpnWatcher(context: Context, cm: ConnectivityManager) {
        vpnNetworkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "External VPN available — releasing explicit transport binding")
                releasePreferredBinding(cm, reconnect = true)
            }

            override fun onLost(network: Network) {
                // Let Android settle the new default network before restoring a preference.
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isExternalVpnActive(context) &&
                        getPreferredTransport(context) != TRANSPORT_DEFAULT
                    ) {
                        bindToPreferredNetwork(context)
                    }
                }, 250)
            }
        }
        vpnNetworkCallback = callback

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            vpnNetworkCallback = null
            Log.w(TAG, "Failed to watch external VPN state: ${e.message}")
        }
    }

    private fun releasePreferredBinding(cm: ConnectivityManager, reconnect: Boolean) {
        val hadPreferredRequest = preferredNetworkCallback != null || boundNetwork != null
        preferredNetworkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        preferredNetworkCallback = null
        boundNetwork = null
        cm.bindProcessToNetwork(null)
        if (reconnect && hadPreferredRequest) {
            WebSocketService.instance?.forceReconnect()
        }
    }

    fun isBound(): Boolean = boundNetwork != null

    fun getBoundNetwork(): android.net.Network? = boundNetwork

    /**
     * Returns a CertificatePinner for api.stealthx.tech.
     *
     * Pins:
     *   - Leaf cert      (current Let's Encrypt cert)
     *   - R12 CA         (Let's Encrypt intermediate — backup for leaf rotation)
     *   - ISRG Root X1   (long-lived root — backup for intermediate rotation)
     *
     * When Let's Encrypt renews the leaf, R12 and root pins keep the app working.
     * App update required only when Let's Encrypt retires R12.
     */
    fun buildCertificatePinner(): CertificatePinner = CertificatePinner.Builder()
        .add("api.stealthx.tech", "sha256/1e85xNSEj+dcImOJS0iNkfMZOrZdvJJzzPCqT1/CZDc=")
        .add("api.stealthx.tech", "sha256/kZwN96eHtZftBWrOZUsd6cA4es80n3NzSk/XtYz2EqQ=")
        .add("api.stealthx.tech", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
        .build()

    @JvmOverloads
    fun buildPinnedClient(
        connectTimeoutSec: Long = 10,
        readTimeoutSec: Long = 10
    ): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSec, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, java.util.concurrent.TimeUnit.SECONDS)
        .apply { if (com.securecall.app.BuildConfig.CERTIFICATE_PINNING) certificatePinner(buildCertificatePinner()) }
        .build()
}

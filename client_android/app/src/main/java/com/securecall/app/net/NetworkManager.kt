package com.securecall.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import okhttp3.CertificatePinner

/**
 * Manages network interface selection for StealthX traffic.
 * Premium feature: route app traffic through eSIM or specific network interface.
 *
 * Default = no binding (Android decides). WiFi/Cellular = explicit bind.
 * When bound network is lost, falls back to default and triggers WS reconnect.
 */
object NetworkManager {
    private const val TAG = "NetworkManager"
    private const val PREFS = "securecall_prefs"
    private const val KEY_PREFERRED_TRANSPORT = "preferred_network_transport"
    private const val KEY_ESIM_ROUTING = "esim_routing_enabled"

    const val TRANSPORT_DEFAULT = "default"
    const val TRANSPORT_WIFI = "wifi"
    const val TRANSPORT_CELLULAR = "cellular"
    const val TRANSPORT_ESIM = "esim"

    private var boundNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun getPreferredTransport(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFERRED_TRANSPORT, TRANSPORT_DEFAULT) ?: TRANSPORT_DEFAULT
    }

    fun setPreferredTransport(context: Context, transport: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PREFERRED_TRANSPORT, transport).apply()
        Log.d(TAG, "Preferred transport set to: $transport")
    }

    fun isEsimRoutingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ESIM_ROUTING, false)
    }

    fun setEsimRouting(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ESIM_ROUTING, enabled).apply()
        if (enabled) bindToPreferredNetwork(context) else unbind(context)
    }

    fun getActiveNetworkInfo(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "No network"
        val caps = cm.getNetworkCapabilities(network) ?: return "Unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }
    }

    /**
     * Apply network binding based on preferred transport.
     * Default = no binding (Android decides the best network).
     * WiFi/Cellular/eSIM = explicit bind via requestNetwork + bindProcessToNetwork.
     */
    fun bindToPreferredNetwork(context: Context) {
        val transport = getPreferredTransport(context)

        // Default = no binding — let Android handle network selection
        if (transport == TRANSPORT_DEFAULT) {
            unbind(context)
            return
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Clean up previous callback before registering new one
        networkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        networkCallback = null
        boundNetwork = null

        val requestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        when (transport) {
            TRANSPORT_WIFI -> requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            TRANSPORT_CELLULAR, TRANSPORT_ESIM -> requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
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
        networkCallback = callback

        try {
            cm.requestNetwork(requestBuilder.build(), callback)
            Log.d(TAG, "Requested network binding: $transport")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request network: ${e.message}")
        }
    }

    fun unbind(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        networkCallback = null
        boundNetwork = null
        cm.bindProcessToNetwork(null)
        Log.d(TAG, "Network binding released — using system default")
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

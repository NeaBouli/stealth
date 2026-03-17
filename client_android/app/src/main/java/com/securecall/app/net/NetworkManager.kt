package com.securecall.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

/**
 * Manages network interface selection for StealthX traffic.
 * Premium feature: route app traffic through eSIM or specific network interface.
 */
object NetworkManager {
    private const val TAG = "NetworkManager"
    private const val PREFS = "securecall_prefs"
    private const val KEY_PREFERRED_TRANSPORT = "preferred_network_transport"
    private const val KEY_ESIM_ROUTING = "esim_routing_enabled"

    // Transport types
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
     * Bind app traffic to preferred network interface.
     * Uses ConnectivityManager.bindProcessToNetwork() for app-wide binding.
     */
    fun bindToPreferredNetwork(context: Context) {
        val transport = getPreferredTransport(context)
        if (transport == TRANSPORT_DEFAULT) {
            unbind(context)
            return
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val requestBuilder = NetworkRequest.Builder()

        when (transport) {
            TRANSPORT_WIFI -> requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            TRANSPORT_CELLULAR, TRANSPORT_ESIM -> requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
        requestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        // Clean up previous callback
        networkCallback?.let { cm.unregisterNetworkCallback(it) }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Preferred network available: $transport")
                boundNetwork = network
                cm.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Preferred network lost: $transport — falling back to default")
                boundNetwork = null
                cm.bindProcessToNetwork(null)
            }
        }
        networkCallback = callback

        try {
            cm.requestNetwork(requestBuilder.build(), callback)
            Log.d(TAG, "Requested network binding: $transport")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request network", e)
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
        Log.d(TAG, "Network binding released")
    }

    fun isBound(): Boolean = boundNetwork != null
}

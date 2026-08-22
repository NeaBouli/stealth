package com.securecall.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Lifecycle-scoped observer that reports whether the device's current default
 * network routes through an Android system VPN
 * ([NetworkCapabilities.TRANSPORT_VPN]). In the Play flavor that VPN is
 * supplied externally; the direct Premium APK can provide its own tunnel.
 *
 * This is a pure transport-status reflection. It never claims provider trust
 * or anonymity and does not install or download VPN code.
 *
 * Starts on ON_START and stops on ON_STOP of the given [LifecycleOwner].
 * On start an immediate state evaluation is performed. On stop the state is
 * reset to inactive so no stale "protected" indicator can survive. All
 * listener callbacks are dispatched on the main thread.
 */
class ExternalVpnMonitor(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val listener: (Boolean) -> Unit
) : DefaultLifecycleObserver {

    /** Events that can change the observed VPN state. */
    internal enum class VpnEvent { CAPABILITIES, STOP }

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var registered = false
    private var active = false
    private var hasReportedState = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            applyNetworkState(reduceVpnActive(VpnEvent.CAPABILITIES, caps.hasVpnTransport()))
        }

        override fun onLost(network: Network) {
            refreshFromSystem()
        }

        override fun onUnavailable() {
            refreshFromSystem()
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = start()

    override fun onStop(owner: LifecycleOwner) = stop()

    /** Register the default-network callback and evaluate the current state at once. */
    fun start() {
        if (registered) return
        val cm = connectivityManager ?: return
        try {
            cm.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: Exception) {
            Log.w(TAG, "registerDefaultNetworkCallback failed: ${e.message}")
            return
        }
        // Immediate evaluation: the async callback may lag behind the real state.
        applyNetworkState(reduceVpnActive(VpnEvent.CAPABILITIES, isVpnTransportActive(cm)))
    }

    /** Unregister and reset to inactive so no stale protected state remains. */
    fun stop() {
        if (registered) {
            connectivityManager?.let {
                try { it.unregisterNetworkCallback(callback) } catch (_: Exception) {}
            }
            registered = false
        }
        applyState(reduceVpnActive(VpnEvent.STOP, hasVpnTransport = false))
    }

    private fun refreshFromSystem() {
        mainHandler.post {
            if (registered) {
                val cm = connectivityManager
                applyState(reduceVpnActive(VpnEvent.CAPABILITIES, cm != null && isVpnTransportActive(cm)))
            }
        }
    }

    private fun applyNetworkState(newState: Boolean) {
        runOnMain {
            if (registered) applyStateOnMain(newState)
        }
    }

    private fun applyState(newState: Boolean) = runOnMain { applyStateOnMain(newState) }

    private fun applyStateOnMain(newState: Boolean) {
        if (newState == active && hasReportedState) return
        active = newState
        hasReportedState = true
        listener(newState)
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    internal companion object {
        private const val TAG = "ExternalVpnMonitor"

        internal fun NetworkCapabilities.hasVpnTransport(): Boolean =
            hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        /** Pure state transition — deterministic and unit-testable. */
        internal fun reduceVpnActive(
            event: VpnEvent,
            hasVpnTransport: Boolean
        ): Boolean = when (event) {
            VpnEvent.CAPABILITIES -> hasVpnTransport
            VpnEvent.STOP -> false
        }

        internal fun isVpnTransportActive(cm: ConnectivityManager): Boolean {
            val network = cm.activeNetwork ?: return false
            return cm.getNetworkCapabilities(network)?.hasVpnTransport() == true
        }
    }
}

package com.securecall.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class NetworkManagerTest {
    @Test
    fun `legacy and unknown transports fall back to system default`() {
        assertEquals(
            NetworkManager.TRANSPORT_DEFAULT,
            NetworkManager.normalizePreferredTransport("esim")
        )
        assertEquals(
            NetworkManager.TRANSPORT_DEFAULT,
            NetworkManager.normalizePreferredTransport("unknown")
        )
        assertEquals(
            NetworkManager.TRANSPORT_DEFAULT,
            NetworkManager.normalizePreferredTransport(null)
        )
    }

    @Test
    fun `recognized transports remain selectable`() {
        assertEquals(
            NetworkManager.TRANSPORT_WIFI,
            NetworkManager.normalizePreferredTransport(NetworkManager.TRANSPORT_WIFI)
        )
        assertEquals(
            NetworkManager.TRANSPORT_CELLULAR,
            NetworkManager.normalizePreferredTransport(NetworkManager.TRANSPORT_CELLULAR)
        )
    }

    @Test
    fun `external VPN prevents explicit process network binding`() {
        assertFalse(
            NetworkManager.shouldBindPreferredNetwork(NetworkManager.TRANSPORT_WIFI, true)
        )
        assertFalse(
            NetworkManager.shouldBindPreferredNetwork(NetworkManager.TRANSPORT_CELLULAR, true)
        )
        assertTrue(
            NetworkManager.shouldBindPreferredNetwork(NetworkManager.TRANSPORT_WIFI, false)
        )
        assertTrue(
            NetworkManager.shouldBindPreferredNetwork(NetworkManager.TRANSPORT_CELLULAR, false)
        )
        assertFalse(
            NetworkManager.shouldBindPreferredNetwork(NetworkManager.TRANSPORT_DEFAULT, false)
        )
    }

    @Test
    fun `vpn transport is reported before underlying wifi or cellular`() {
        // A system VPN tunnels over WiFi/cellular, so capabilities list both
        // transports; the display must report VPN first.
        val capabilities = mock<NetworkCapabilities> {
            on { hasTransport(NetworkCapabilities.TRANSPORT_VPN) }.thenReturn(true)
            on { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }.thenReturn(true)
            on { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }.thenReturn(true)
        }
        val network = mock<Network>()
        val connectivityManager = mock<ConnectivityManager> {
            on { activeNetwork }.thenReturn(network)
            on { getNetworkCapabilities(network) }.thenReturn(capabilities)
        }
        val context = mock<Context> {
            on { getSystemService(Context.CONNECTIVITY_SERVICE) }.thenReturn(connectivityManager)
        }

        assertEquals("VPN", NetworkManager.getActiveNetworkInfo(context))
    }

    @Test
    fun `wifi is reported when no vpn transport is present`() {
        val capabilities = mock<NetworkCapabilities> {
            on { hasTransport(NetworkCapabilities.TRANSPORT_VPN) }.thenReturn(false)
            on { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }.thenReturn(true)
        }
        val network = mock<Network>()
        val connectivityManager = mock<ConnectivityManager> {
            on { activeNetwork }.thenReturn(network)
            on { getNetworkCapabilities(network) }.thenReturn(capabilities)
        }
        val context = mock<Context> {
            on { getSystemService(Context.CONNECTIVITY_SERVICE) }.thenReturn(connectivityManager)
        }

        assertEquals("WiFi", NetworkManager.getActiveNetworkInfo(context))
    }
}

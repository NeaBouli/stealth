package com.securecall.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

package com.securecall.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireGuardConfigParserTest {
    private val validKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    @Test
    fun `parses complete WireGuard configuration`() {
        val parsed = WireGuardConfigParser.parse(
            """
            [Interface]
            PrivateKey = $validKey
            Address = 10.66.66.2/32
            DNS = 1.1.1.1

            [Peer]
            PublicKey = $validKey
            Endpoint = vpn.example.com:51820
            AllowedIPs = 0.0.0.0/0
            """.trimIndent()
        )

        requireNotNull(parsed)
        assertEquals("vpn.example.com", parsed.endpoint)
        assertEquals(51820, parsed.port)
        assertEquals(validKey, parsed.clientPrivateKey)
        assertEquals(validKey, parsed.serverPublicKey)
    }

    @Test
    fun `supports bracketed IPv6 endpoint`() {
        assertEquals("2001:db8::1" to 51820, WireGuardConfigParser.parseEndpoint("[2001:db8::1]:51820"))
    }

    @Test
    fun `rejects missing keys and invalid ports`() {
        assertNull(WireGuardConfigParser.parse("[Peer]\nEndpoint = host:51820"))
        assertNull(WireGuardConfigParser.parseEndpoint("host:70000"))
    }

    @Test
    fun `field defaults match the configured address family`() {
        val parsed = WireGuardConfigParser.fromFields(
            endpoint = "vpn.example.com",
            port = "51820",
            serverPublicKey = validKey,
            clientPrivateKey = validKey,
            existingPrivateKey = "",
            dns = "",
            allowedIps = "",
            clientAddress = ""
        )

        requireNotNull(parsed)
        assertEquals("0.0.0.0/0", parsed.allowedIps)
    }

    @Test
    fun `preserves dual stack addresses dns and routes from pasted config`() {
        val parsed = WireGuardConfigParser.parse(
            """
            [Interface]
            PrivateKey = $validKey
            Address = 10.66.66.2/32, fd00::2/128
            DNS = 1.1.1.1, 2606:4700:4700::1111

            [Peer]
            PublicKey = $validKey
            Endpoint = vpn.example.com:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            """.trimIndent()
        )

        requireNotNull(parsed)
        assertEquals("10.66.66.2/32, fd00::2/128", parsed.clientAddress)
        assertEquals("1.1.1.1, 2606:4700:4700::1111", parsed.dns)
        assertEquals("0.0.0.0/0, ::/0", parsed.allowedIps)
    }

    @Test
    fun `rejects malformed WireGuard keys before storage`() {
        assertNull(
            WireGuardConfigParser.fromFields(
                endpoint = "vpn.example.com",
                port = "51820",
                serverPublicKey = "not-a-key",
                clientPrivateKey = validKey,
                existingPrivateKey = "",
                dns = "1.1.1.1",
                allowedIps = "0.0.0.0/0, ::/0",
                clientAddress = "10.66.66.2/32"
            )
        )
    }
}

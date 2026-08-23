package com.securecall.app.vpn

import com.wireguard.crypto.Key

internal object WireGuardConfigParser {
    fun parse(raw: String, savedPrivateKey: String = ""): WireGuardConfigData? {
        var section = ""
        var privateKey = savedPrivateKey
        var address = ""
        var dns = "1.1.1.1"
        var publicKey = ""
        var endpoint = ""
        var allowedIps = ""

        raw.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                when {
                    line.equals("[Interface]", ignoreCase = true) -> section = "interface"
                    line.equals("[Peer]", ignoreCase = true) -> section = "peer"
                    '=' in line -> {
                        val key = line.substringBefore('=').trim()
                        val value = line.substringAfter('=').trim()
                        when {
                            section == "interface" && key.equals("PrivateKey", true) -> privateKey = value
                            section == "interface" && key.equals("Address", true) ->
                                address = value
                            section == "interface" && key.equals("DNS", true) ->
                                dns = value.ifEmpty { "1.1.1.1" }
                            section == "peer" && key.equals("PublicKey", true) -> publicKey = value
                            section == "peer" && key.equals("Endpoint", true) -> endpoint = value
                            section == "peer" && key.equals("AllowedIPs", true) -> allowedIps = value
                        }
                    }
                }
            }

        val parsedEndpoint = parseEndpoint(endpoint) ?: return null
        if (!isValidKey(privateKey) || !isValidKey(publicKey)) return null
        val normalizedAddress = address.ifBlank { "10.66.66.2/32" }
        return WireGuardConfigData(
            endpoint = parsedEndpoint.first,
            port = parsedEndpoint.second,
            serverPublicKey = publicKey,
            clientPrivateKey = privateKey,
            dns = dns,
            allowedIps = allowedIps.ifBlank { defaultAllowedIps(normalizedAddress) },
            clientAddress = normalizedAddress
        )
    }

    fun fromFields(
        endpoint: String,
        port: String,
        serverPublicKey: String,
        clientPrivateKey: String,
        existingPrivateKey: String,
        dns: String,
        allowedIps: String,
        clientAddress: String
    ): WireGuardConfigData? {
        val cleanEndpoint = endpoint.trim()
        val cleanPublicKey = serverPublicKey.trim()
        val effectivePrivateKey = clientPrivateKey.trim().ifEmpty { existingPrivateKey }
        val parsedPort = port.trim().toIntOrNull() ?: return null
        if (cleanEndpoint.isEmpty() || !isValidKey(cleanPublicKey) || !isValidKey(effectivePrivateKey)) return null
        if (parsedPort !in 1..65535) return null
        val normalizedAddress = clientAddress.trim().ifEmpty { "10.66.66.2/32" }
        val normalizedAllowedIps = allowedIps.trim().ifEmpty {
            defaultAllowedIps(normalizedAddress)
        }
        return WireGuardConfigData(
            endpoint = cleanEndpoint,
            port = parsedPort,
            serverPublicKey = cleanPublicKey,
            clientPrivateKey = effectivePrivateKey,
            dns = dns.trim().ifEmpty { "1.1.1.1" },
            allowedIps = normalizedAllowedIps,
            clientAddress = normalizedAddress
        )
    }

    internal fun parseEndpoint(value: String): Pair<String, Int>? {
        val clean = value.trim()
        if (clean.startsWith("[") && "]:" in clean) {
            val host = clean.substringAfter('[').substringBefore(']')
            val port = clean.substringAfter("]:").toIntOrNull() ?: return null
            return if (host.isNotBlank() && port in 1..65535) host to port else null
        }
        val host = clean.substringBeforeLast(':', missingDelimiterValue = "")
        val port = clean.substringAfterLast(':').toIntOrNull() ?: return null
        return if (host.isNotBlank() && port in 1..65535) host to port else null
    }

    private fun isValidKey(value: String): Boolean =
        value.isNotBlank() && runCatching { Key.fromBase64(value) }.isSuccess

    private fun defaultAllowedIps(addresses: String): String {
        val families = addresses.split(',').map(String::trim).filter(String::isNotEmpty)
        val routes = buildList {
            if (families.any { ':' !in it }) add("0.0.0.0/0")
            if (families.any { ':' in it }) add("::/0")
        }
        return routes.ifEmpty { listOf("0.0.0.0/0") }.joinToString(", ")
    }
}

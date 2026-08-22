package com.securecall.app.vpn

internal data class WireGuardConfigData(
    val endpoint: String,
    val port: Int,
    val serverPublicKey: String,
    val clientPrivateKey: String,
    val dns: String,
    val allowedIps: String,
    val clientAddress: String
)

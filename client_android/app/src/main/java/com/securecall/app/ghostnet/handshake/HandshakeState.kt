package com.securecall.app.ghostnet.handshake

/**
 * PATCH 199:
 * Einfache Zustände für den GhostNet-Handshake.
 */
enum class HandshakeState {
    IDLE,
    OUTGOING,
    INCOMING,
    ESTABLISHED,
    FAILED
}

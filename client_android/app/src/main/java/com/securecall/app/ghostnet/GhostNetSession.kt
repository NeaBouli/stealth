package com.securecall.app.ghostnet

/**
 * BACKEND-24 — GhostNetSession
 *
 * Speichert ghostNetId + relayHints vom Server.
 */

data class GhostNetRelayHint(
    val host: String,
    val port: Int
)

object GhostNetSession {
    var ghostNetId: String? = null
    var relayHints: List<GhostNetRelayHint> = emptyList()

    fun setSession(id: String, hints: List<GhostNetRelayHint>) {
        ghostNetId = id
        relayHints = hints
    }

    fun clear() {
        ghostNetId = null
        relayHints = emptyList()
    }
}

package com.securecall.app.net

import android.util.Log
import org.json.JSONArray
import org.webrtc.PeerConnection

/**
 * ICE server provider. Servers are injected via the REGISTERED WebSocket message
 * (delivered by the signaling server after successful REGISTER).
 *
 * No HTTP calls — /ice-servers is admin-only (H-01 security fix).
 */
object IceServerFetcher {

    private const val TAG = "ICE_FETCH"

    @Volatile private var cachedServers: List<PeerConnection.IceServer>? = null
    @Volatile private var cacheTimestamp: Long = 0L
    private const val CACHE_TTL_MS = 3600_000L // 1 hour

    /**
     * Returns cached ICE servers if available and not expired.
     * Servers are populated by [injectFromRegistered] after WS REGISTER.
     *
     * @return List of IceServer or null (caller should use STUN-only fallback)
     */
    fun fetch(): List<PeerConnection.IceServer>? {
        val cached = cachedServers
        if (cached != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            Log.d(TAG, "Using cached ICE servers (${cached.size} servers)")
            com.securecall.app.debug.SecLogManager.log("ICE", "Cached: ${cached.size} servers")
            return cached
        }
        Log.d(TAG, "No cached ICE servers — waiting for REGISTERED message")
        return null
    }

    /**
     * No-op. ICE servers are delivered via WebSocket REGISTERED message.
     * Kept for backward compatibility with callers.
     */
    fun prefetch() {
        Log.d(TAG, "Prefetch skipped — ICE servers delivered via WS REGISTERED message")
    }

    /**
     * Inject ICE servers received from the REGISTERED WebSocket message.
     * This is the only source of ICE/TURN credentials (H-01 security fix).
     */
    fun injectFromRegistered(iceServersArray: JSONArray) {
        val servers = parseIceServers(iceServersArray)
        if (servers.isNotEmpty()) {
            cachedServers = servers
            cacheTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Injected ${servers.size} ICE servers from REGISTERED message")
            com.securecall.app.debug.SecLogManager.log("ICE", "Injected from WS: ${servers.size} servers")
        }
    }

    private fun parseIceServers(array: JSONArray): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val urls = obj.getString("urls")
                val builder = PeerConnection.IceServer.builder(urls)

                if (obj.has("username") && obj.has("credential")) {
                    builder.setUsername(obj.getString("username"))
                    builder.setPassword(obj.getString("credential"))
                }

                servers.add(builder.createIceServer())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse ICE server at index $i: ${e.message}")
            }
        }
        return servers
    }
}

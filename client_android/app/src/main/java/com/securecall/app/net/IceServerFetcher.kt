package com.securecall.app.net

import android.util.Log
import com.securecall.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.util.concurrent.TimeUnit

/**
 * Fetches ICE (STUN/TURN) server credentials from the signaling backend at runtime.
 * This eliminates hardcoded TURN credentials from the APK.
 *
 * Endpoint: GET /ice-servers on the signaling server
 * Returns: { "iceServers": [{ "urls": "...", "username": "...", "credential": "..." }] }
 */
object IceServerFetcher {

    private const val TAG = "ICE_FETCH"

    // Cache: credentials are valid for ~1 hour (Metered.ca default TTL)
    @Volatile private var cachedServers: List<PeerConnection.IceServer>? = null
    @Volatile private var cacheTimestamp: Long = 0L
    private const val CACHE_TTL_MS = 3600_000L // 1 hour

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch ICE servers from backend. Returns cached result if still valid.
     * Call this on a background thread — performs network I/O.
     *
     * @return List of IceServer or null if fetch failed (caller should use STUN-only fallback)
     */
    fun fetch(): List<PeerConnection.IceServer>? {
        // Return cache if still valid
        val cached = cachedServers
        if (cached != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            Log.d(TAG, "Using cached ICE servers (${cached.size} servers)")
            return cached
        }

        val baseUrl = BuildConfig.SIGNAL_WS_URL
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .replace("/signal", "")

        val url = "$baseUrl/ice-servers"
        Log.d(TAG, "Fetching ICE servers from $url")

        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "ICE server fetch failed: HTTP ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val serversArray = json.getJSONArray("iceServers")

            val servers = mutableListOf<PeerConnection.IceServer>()
            for (i in 0 until serversArray.length()) {
                val obj = serversArray.getJSONObject(i)
                val urls = obj.getString("urls")
                val builder = PeerConnection.IceServer.builder(urls)

                if (obj.has("username") && obj.has("credential")) {
                    builder.setUsername(obj.getString("username"))
                    builder.setPassword(obj.getString("credential"))
                }

                servers.add(builder.createIceServer())
            }

            Log.d(TAG, "Fetched ${servers.size} ICE servers")
            cachedServers = servers
            cacheTimestamp = System.currentTimeMillis()
            servers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch ICE servers", e)
            null
        }
    }
}

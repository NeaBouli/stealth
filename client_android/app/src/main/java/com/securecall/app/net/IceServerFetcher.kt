package com.securecall.app.net

import android.util.Log
import com.securecall.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.util.concurrent.TimeUnit

/**
 * Fetches ICE (STUN/TURN) server credentials from the signaling backend at runtime.
 * This eliminates hardcoded TURN credentials from the APK.
 *
 * Endpoint: GET /ice-servers on the signaling server
 * Returns: { "iceServers": [{ "urls": "...", "username": "...", "credential": "..." }] }
 *
 * BUG-040: Previous version had 5s timeout and no retries, causing silent failures
 * on slow connections or Railway cold-starts. Now uses 10s timeout + 3 retries.
 */
object IceServerFetcher {

    private const val TAG = "ICE_FETCH"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 1000L

    // Cache: credentials are valid for ~1 hour (Metered.ca default TTL)
    @Volatile private var cachedServers: List<PeerConnection.IceServer>? = null
    @Volatile private var cacheTimestamp: Long = 0L
    private const val CACHE_TTL_MS = 3600_000L // 1 hour

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch ICE servers from backend with retry logic. Returns cached result if still valid.
     * Call this on a background thread — performs network I/O.
     *
     * @return List of IceServer or null if all retries failed (caller should use STUN-only fallback)
     */
    fun fetch(): List<PeerConnection.IceServer>? {
        // Return cache if still valid
        val cached = cachedServers
        if (cached != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            Log.d(TAG, "Using cached ICE servers (${cached.size} servers)")
            com.securecall.app.debug.SecLogManager.log("ICE", "Cached: ${cached.size} servers")
            return cached
        }

        val baseUrl = BuildConfig.SIGNAL_WS_URL
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .replace("/signal", "")

        val url = "$baseUrl/ice-servers"

        for (attempt in 1..MAX_RETRIES) {
            try {
                Log.d(TAG, "Fetching ICE servers from $url (attempt $attempt/$MAX_RETRIES)")
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val msg = "HTTP ${response.code}"
                    Log.w(TAG, "ICE server fetch failed: $msg (attempt $attempt)")
                    com.securecall.app.debug.SecLogManager.log("ICE", "Fetch failed: $msg (attempt $attempt)")
                    response.close()
                    if (attempt < MAX_RETRIES) Thread.sleep(RETRY_DELAY_MS)
                    continue
                }

                val body = response.body?.string()
                response.close()
                if (body.isNullOrEmpty()) {
                    Log.w(TAG, "ICE server fetch: empty body (attempt $attempt)")
                    if (attempt < MAX_RETRIES) Thread.sleep(RETRY_DELAY_MS)
                    continue
                }

                val json = JSONObject(body)
                val serversArray = json.getJSONArray("iceServers")
                val servers = parseIceServers(serversArray)

                if (servers.isNotEmpty()) {
                    Log.d(TAG, "Fetched ${servers.size} ICE servers on attempt $attempt")
                    com.securecall.app.debug.SecLogManager.log("ICE", "Fetched: ${servers.size} servers")
                    cachedServers = servers
                    cacheTimestamp = System.currentTimeMillis()
                    return servers
                }

                Log.w(TAG, "ICE server fetch: parsed 0 servers (attempt $attempt)")
            } catch (e: Exception) {
                val msg = e.message ?: "unknown"
                Log.e(TAG, "ICE server fetch error (attempt $attempt): $msg")
                com.securecall.app.debug.SecLogManager.log("ICE", "Fetch error ($attempt): $msg")
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) {}
                }
            }
        }

        Log.e(TAG, "All $MAX_RETRIES ICE server fetch attempts failed — using STUN fallback")
        com.securecall.app.debug.SecLogManager.log("ICE", "All $MAX_RETRIES fetch attempts FAILED")
        return null
    }

    /**
     * Pre-fetch ICE servers in background. Call after WS connect so
     * credentials are cached before a call starts.
     */
    fun prefetch() {
        Thread {
            try {
                val result = fetch()
                Log.d(TAG, "Prefetch result: ${result?.size ?: 0} servers")
            } catch (e: Exception) {
                Log.w(TAG, "Prefetch failed: ${e.message}")
            }
        }.start()
    }

    /**
     * Inject ICE servers received from the REGISTERED WebSocket message.
     * This avoids the HTTP GET /ice-servers call entirely (H-01 security fix).
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

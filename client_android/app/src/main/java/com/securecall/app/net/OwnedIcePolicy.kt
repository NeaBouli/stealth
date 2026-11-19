package com.securecall.app.net

import java.net.URI
import java.net.URISyntaxException

/**
 * Owned-test dynamic ICE validation (SC-OWNED-CALL-DELTA-20261119).
 *
 * Active only when an owned-test debug build supplies a nonempty owned host
 * (`BuildConfig.DEBUG` plus `OWNED_TEST_PIN_HOST`). Every supplied ICE URL must then
 * point at that exact numeric host with an explicit 1024..65535 port; otherwise the
 * whole server list is refused. Only URLs and the TLS hostname override are inspected:
 * TURN usernames/passwords stay inside the server object and are never read or logged.
 * Pure Kotlin (java.net only) so the cases run on the plain JVM.
 */
internal object OwnedIcePolicy {
    private val OWNED_HOSTS = setOf("10.0.2.2", "127.0.0.1")
    private val QUERY_BY_SCHEME: Map<String, Set<String?>> = mapOf(
        "stun" to setOf(null),
        "turn" to setOf(null, "transport=udp", "transport=tcp"),
        "turns" to setOf(null, "transport=tcp")
    )

    /** Effective owned host, or empty when owned mode is off (always empty in release). */
    fun ownedHost(debug: Boolean, configuredHost: String): String =
        if (debug && configuredHost.isNotEmpty()) configuredHost else ""

    /**
     * True when owned mode is off, or when every server is entirely owned.
     * [servers] are (urls, tlsHostname) pairs; an empty server list is accepted so
     * the existing STUN-fallback/relay-only decision still applies.
     */
    fun accepts(servers: List<Pair<List<String>, String?>>, ownedHost: String): Boolean {
        if (ownedHost.isEmpty()) return true
        if (ownedHost !in OWNED_HOSTS) return false
        return servers.all { (urls, tlsHostname) ->
            tlsHostname.isNullOrEmpty() && urls.isNotEmpty() && urls.all { isOwnedIceUrl(it, ownedHost) }
        }
    }

    fun isOwnedIceUrl(url: String, ownedHost: String): Boolean {
        val outer = parse(url) ?: return false
        val allowedQueries = QUERY_BY_SCHEME[outer.scheme] ?: return false
        if (!outer.isOpaque || outer.rawFragment != null || outer.toString() != url) return false
        val ssp = outer.rawSchemeSpecificPart
        val authority = ssp.substringBefore('?')
        val query = if ('?' in ssp) ssp.substringAfter('?') else null
        if (query !in allowedQueries) return false
        val inner = parse("ice://$authority") ?: return false
        val port = inner.port
        return inner.rawUserInfo == null && inner.host == ownedHost && port in 1024..65535 &&
            inner.rawPath.isNullOrEmpty() && inner.rawQuery == null && inner.rawFragment == null &&
            inner.rawAuthority == "$ownedHost:$port"
    }

    private fun parse(value: String): URI? = try {
        URI(value)
    } catch (_: URISyntaxException) {
        null
    }
}

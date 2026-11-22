package com.securecall.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC-OWNED-CALL-DELTA-20261119: persisted owned dynamic ICE validator cases.
 * Depends only on [OwnedIcePolicy] + JUnit, so it also runs outside Gradle.
 */
class OwnedIcePolicyTest {
    @Test
    fun `owned mode is off outside debug or without owned host`() {
        assertEquals("", OwnedIcePolicy.ownedHost(false, HOST))
        assertEquals("", OwnedIcePolicy.ownedHost(true, ""))
        assertEquals(HOST, OwnedIcePolicy.ownedHost(true, HOST))
        assertTrue(OwnedIcePolicy.accepts(listOf(listOf("stun:stun.l.google.com:19302") to null), ""))
    }

    @Test
    fun `valid owned stun turn and turns shapes are accepted`() {
        listOf(
            "stun:$HOST:3478", "turn:$HOST:3478", "turn:$HOST:3478?transport=udp",
            "turn:$HOST:3478?transport=tcp", "turns:$HOST:5349", "turns:$HOST:5349?transport=tcp",
            "stun:$HOST:1024", "stun:$HOST:65535"
        ).forEach { assertTrue(it, OwnedIcePolicy.isOwnedIceUrl(it, HOST)) }
        assertTrue(OwnedIcePolicy.isOwnedIceUrl("turn:127.0.0.1:3478", "127.0.0.1"))
        assertTrue(OwnedIcePolicy.accepts(listOf(listOf("stun:$HOST:3478", "turn:$HOST:3478") to ""), HOST))
    }

    @Test
    fun `bad owned urls are rejected`() {
        listOf(
            "stun:stun.l.google.com:19302", "turn:turn.example.test:3478", "stun:127.0.0.1:3478",
            "stun:10.0.2.3:3478", "stun:$HOST", "stun:$HOST:", "stun:$HOST:80", "stun:$HOST:1023",
            "stun:$HOST:65536", "stun:$HOST:03478", "stun://$HOST:3478", "STUN:$HOST:3478",
            "stuns:$HOST:3478", "http://$HOST:3478", "stun:user@$HOST:3478", "turn:u:p@$HOST:3478",
            "stun:$HOST:3478/path", "stun:$HOST:3478#frag", "stun:$HOST:3478?transport=udp",
            "turn:$HOST:3478?transport=tls", "turn:$HOST:3478?transport=udp&x=1",
            "turns:$HOST:5349?transport=udp", "turn:$HOST:3478@evil.test:3478", "stun:[::1]:3478",
            "stun:$HOST:3478 ", "stun:localhost:3478", "turn:$HOST.nip.io:3478", ""
        ).forEach { assertFalse(it, OwnedIcePolicy.isOwnedIceUrl(it, HOST)) }
    }

    @Test
    fun `mixed valid and invalid servers reject the whole list`() {
        val good = listOf("turn:$HOST:3478") to null
        assertFalse(OwnedIcePolicy.accepts(listOf(good, listOf("stun:stun.l.google.com:19302") to null), HOST))
        assertFalse(OwnedIcePolicy.accepts(listOf(listOf("turn:$HOST:3478", "turn:public.example.test:3478") to null), HOST))
        assertFalse("alternate TLS host", OwnedIcePolicy.accepts(listOf(listOf("turns:$HOST:5349") to "evil.test"), HOST))
        assertFalse("server without urls", OwnedIcePolicy.accepts(listOf(emptyList<String>() to null), HOST))
        assertFalse("non-owned configured host", OwnedIcePolicy.accepts(listOf(good), "192.168.1.2"))
    }

    @Test
    fun `empty owned list is left to the existing fallback decision`() {
        assertTrue(OwnedIcePolicy.accepts(emptyList(), HOST))
    }

    private companion object {
        const val HOST = "10.0.2.2"
    }
}

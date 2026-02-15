package com.securecall.app.ghostnet.crypto

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class SessionCipherContextTest {

    private fun makeCtx() = SessionCipherContext(
        sessionId = "test",
        keyId = 1,
        rxKey = ByteArray(32),
        txKey = ByteArray(32)
    )

    @Test
    fun nextNonce_startsAt1() {
        assertEquals(1L, makeCtx().nextNonce())
    }

    @Test
    fun nextNonce_increments() {
        val ctx = makeCtx()
        assertEquals(1L, ctx.nextNonce())
        assertEquals(2L, ctx.nextNonce())
        assertEquals(3L, ctx.nextNonce())
    }

    @Test
    fun nextNonce_threadSafe() {
        val ctx = makeCtx()
        val seen = ConcurrentHashMap.newKeySet<Long>()
        val threads = (1..10).map {
            Thread { repeat(100) { seen.add(ctx.nextNonce()) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1000, seen.size)
    }
}

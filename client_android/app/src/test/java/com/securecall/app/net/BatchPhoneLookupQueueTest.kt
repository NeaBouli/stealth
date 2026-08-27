package com.securecall.app.net

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchPhoneLookupQueueTest {
    @Test
    fun `requests and callbacks remain ordered`() {
        val sent = mutableListOf<List<String>>()
        val callbacks = mutableListOf<String>()
        val queue = BatchPhoneLookupQueue { hashes -> sent.add(hashes); true }

        queue.enqueue(listOf("first")) { callbacks.add("first:${it.size}") }
        queue.enqueue(listOf("second")) { callbacks.add("second:${it.size}") }

        assertEquals(listOf(listOf("first")), sent)
        queue.complete(mapOf("first" to Pair(true, "client-1")))
        assertEquals(listOf(listOf("first"), listOf("second")), sent)
        assertEquals(listOf("first:1"), callbacks)

        queue.complete(emptyMap())
        assertEquals(listOf("first:1", "second:0"), callbacks)
    }

    @Test
    fun `failed send completes empty and advances queue`() {
        val callbacks = mutableListOf<Int>()
        val queue = BatchPhoneLookupQueue { hashes -> hashes.first() != "fail" }

        queue.enqueue(listOf("fail")) { callbacks.add(it.size) }
        queue.enqueue(listOf("next")) { callbacks.add(it.size) }
        queue.complete(mapOf("next" to Pair(false, "client-2")))

        assertEquals(listOf(0, 1), callbacks)
    }

    @Test
    fun `callback failure does not block the next request`() {
        val sent = mutableListOf<List<String>>()
        val queue = BatchPhoneLookupQueue { hashes -> sent.add(hashes); true }

        queue.enqueue(listOf("first")) { error("callback failed") }
        queue.enqueue(listOf("second")) { }

        runCatching { queue.complete(emptyMap()) }

        assertEquals(listOf(listOf("first"), listOf("second")), sent)
    }
}

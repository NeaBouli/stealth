package com.securecall.app.net

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchPhoneLookupQueueTest {
    private class ManualScheduler {
        private val actions = mutableListOf<() -> Unit>()

        fun schedule(@Suppress("UNUSED_PARAMETER") delayMs: Long, action: () -> Unit) {
            actions.add(action)
        }

        fun runAll() {
            val scheduled = actions.toList()
            actions.clear()
            scheduled.forEach { it() }
        }
    }

    @Test
    fun `requests and callbacks remain ordered`() {
        val sent = mutableListOf<List<String>>()
        val callbacks = mutableListOf<String>()
        val scheduler = ManualScheduler()
        val queue = BatchPhoneLookupQueue(
            sendRequest = { hashes -> sent.add(hashes); true },
            scheduleTimeout = scheduler::schedule,
        )

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
        val scheduler = ManualScheduler()
        val queue = BatchPhoneLookupQueue(
            sendRequest = { hashes -> hashes.first() != "fail" },
            scheduleTimeout = scheduler::schedule,
        )

        queue.enqueue(listOf("fail")) { callbacks.add(it.size) }
        queue.enqueue(listOf("next")) { callbacks.add(it.size) }
        queue.complete(mapOf("next" to Pair(false, "client-2")))

        assertEquals(listOf(0, 1), callbacks)
    }

    @Test
    fun `callback failure does not block the next request`() {
        val sent = mutableListOf<List<String>>()
        val scheduler = ManualScheduler()
        val queue = BatchPhoneLookupQueue(
            sendRequest = { hashes -> sent.add(hashes); true },
            scheduleTimeout = scheduler::schedule,
        )

        queue.enqueue(listOf("first")) { error("callback failed") }
        queue.enqueue(listOf("second")) { }

        runCatching { queue.complete(emptyMap()) }

        assertEquals(listOf(listOf("first"), listOf("second")), sent)
    }

    @Test
    fun `missing response times out and advances queue`() {
        val sent = mutableListOf<List<String>>()
        val callbacks = mutableListOf<String>()
        val scheduler = ManualScheduler()
        val queue = BatchPhoneLookupQueue(
            sendRequest = { hashes -> sent.add(hashes); true },
            scheduleTimeout = scheduler::schedule,
        )

        queue.enqueue(listOf("first")) { callbacks.add("first:${it.size}") }
        queue.enqueue(listOf("second")) { callbacks.add("second:${it.size}") }

        scheduler.runAll()

        assertEquals(listOf(listOf("first"), listOf("second")), sent)
        assertEquals(listOf("first:0"), callbacks)
    }

    @Test
    fun `late timeout after response is ignored`() {
        val callbacks = mutableListOf<Int>()
        val scheduler = ManualScheduler()
        val queue = BatchPhoneLookupQueue(
            sendRequest = { true },
            scheduleTimeout = scheduler::schedule,
        )

        queue.enqueue(listOf("first")) { callbacks.add(it.size) }
        queue.complete(mapOf("first" to Pair(true, "client-1")))
        scheduler.runAll()

        assertEquals(listOf(1), callbacks)
    }

    @Test
    fun `disconnect fails active and pending requests once`() {
        val callbacks = mutableListOf<String>()
        val scheduler = ManualScheduler()
        val queue = BatchPhoneLookupQueue(
            sendRequest = { true },
            scheduleTimeout = scheduler::schedule,
        )

        queue.enqueue(listOf("first")) { callbacks.add("first:${it.size}") }
        queue.enqueue(listOf("second")) { callbacks.add("second:${it.size}") }

        queue.failAll()
        scheduler.runAll()

        assertEquals(listOf("first:0", "second:0"), callbacks)
    }
}

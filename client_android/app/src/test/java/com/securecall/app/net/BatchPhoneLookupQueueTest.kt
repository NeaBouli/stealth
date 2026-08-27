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

    private fun queue(
        scheduler: ManualScheduler,
        sent: MutableList<List<String>> = mutableListOf(),
        reconnects: MutableList<Unit> = mutableListOf(),
        send: (List<String>) -> Boolean = { hashes -> sent.add(hashes); true },
    ) = BatchPhoneLookupQueue(
        sendRequest = send,
        scheduleTimeout = scheduler::schedule,
        onProtocolTimeout = { reconnects.add(Unit) },
    )

    @Test
    fun `requests and callbacks remain ordered`() {
        val sent = mutableListOf<List<String>>()
        val callbacks = mutableListOf<String>()
        val scheduler = ManualScheduler()
        val queue = queue(scheduler, sent)

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
        val queue = queue(scheduler, send = { hashes -> hashes.first() != "fail" })

        queue.enqueue(listOf("fail")) { callbacks.add(it.size) }
        queue.enqueue(listOf("next")) { callbacks.add(it.size) }
        queue.complete(mapOf("next" to Pair(false, "client-2")))

        assertEquals(listOf(0, 1), callbacks)
    }

    @Test
    fun `callback failure does not block the next request`() {
        val sent = mutableListOf<List<String>>()
        val scheduler = ManualScheduler()
        val queue = queue(scheduler, sent)

        queue.enqueue(listOf("first")) { error("callback failed") }
        queue.enqueue(listOf("second")) { }

        runCatching { queue.complete(emptyMap()) }

        assertEquals(listOf(listOf("first"), listOf("second")), sent)
    }

    @Test
    fun `missing response drains queue and suspends the connection generation`() {
        val sent = mutableListOf<List<String>>()
        val callbacks = mutableListOf<String>()
        val reconnects = mutableListOf<Unit>()
        val scheduler = ManualScheduler()
        val queue = queue(scheduler, sent, reconnects)

        queue.enqueue(listOf("first")) { callbacks.add("first:${it.size}") }
        queue.enqueue(listOf("second")) { callbacks.add("second:${it.size}") }

        scheduler.runAll()

        assertEquals(listOf(listOf("first")), sent)
        assertEquals(listOf("first:0", "second:0"), callbacks)
        assertEquals(1, reconnects.size)
    }

    @Test
    fun `late timeout after response is ignored`() {
        val callbacks = mutableListOf<Int>()
        val scheduler = ManualScheduler()
        val reconnects = mutableListOf<Unit>()
        val queue = queue(scheduler, reconnects = reconnects, send = { true })

        queue.enqueue(listOf("first")) { callbacks.add(it.size) }
        queue.complete(mapOf("first" to Pair(true, "client-1")))
        scheduler.runAll()

        assertEquals(listOf(1), callbacks)
        assertEquals(0, reconnects.size)
    }

    @Test
    fun `disconnect fails active and pending requests once`() {
        val callbacks = mutableListOf<String>()
        val scheduler = ManualScheduler()
        val queue = queue(scheduler, send = { true })

        queue.enqueue(listOf("first")) { callbacks.add("first:${it.size}") }
        queue.enqueue(listOf("second")) { callbacks.add("second:${it.size}") }

        queue.suspendAndFailAll()
        scheduler.runAll()

        assertEquals(listOf("first:0", "second:0"), callbacks)
    }

    @Test
    fun `requests queued while suspended resume only on new registered connection`() {
        val sent = mutableListOf<List<String>>()
        val callbacks = mutableListOf<String>()
        val scheduler = ManualScheduler()
        val queue = queue(scheduler, sent)

        queue.suspendAndFailAll()
        queue.enqueue(listOf("next")) { callbacks.add("next:${it.size}") }
        assertEquals(emptyList<List<String>>(), sent)

        queue.resume()
        assertEquals(listOf(listOf("next")), sent)
        queue.complete(mapOf("next" to Pair(true, "client-2")))
        assertEquals(listOf("next:1"), callbacks)
    }

    @Test
    fun `late response after timeout cannot complete a new generation request`() {
        val callbacks = mutableListOf<String>()
        val scheduler = ManualScheduler()
        val queue = queue(scheduler, send = { true })

        queue.enqueue(listOf("old")) { callbacks.add("old:${it.size}") }
        scheduler.runAll()
        queue.complete(mapOf("old" to Pair(true, "stale")))
        queue.enqueue(listOf("new")) { callbacks.add("new:${it.size}") }

        assertEquals(listOf("old:0"), callbacks)
        queue.resume()
        queue.complete(mapOf("new" to Pair(true, "fresh")))
        assertEquals(listOf("old:0", "new:1"), callbacks)
    }
}

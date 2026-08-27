package com.securecall.app.net

/** Serializes requests because legacy signaling responses have no request ID. */
internal class BatchPhoneLookupQueue(
    private val sendRequest: (List<String>) -> Boolean,
) {
    private data class Request(
        val hashes: List<String>,
        val callback: (Map<String, Pair<Boolean, String>>) -> Unit,
    )

    private val lock = Any()
    private val pending = ArrayDeque<Request>()
    private var active: Request? = null

    fun enqueue(
        hashes: List<String>,
        callback: (Map<String, Pair<Boolean, String>>) -> Unit,
    ) {
        val next = synchronized(lock) {
            pending.addLast(Request(hashes.toList(), callback))
            takeNextLocked()
        }
        next?.let(::dispatch)
    }

    fun complete(results: Map<String, Pair<Boolean, String>>) {
        val request = synchronized(lock) { active } ?: return
        finish(request, results)
    }

    private fun dispatch(request: Request) {
        if (!runCatching { sendRequest(request.hashes) }.getOrDefault(false)) {
            finish(request, emptyMap())
        }
    }

    private fun finish(
        expected: Request,
        results: Map<String, Pair<Boolean, String>>,
    ) {
        val next = synchronized(lock) {
            if (active !== expected) return
            active = null
            takeNextLocked()
        }
        try {
            expected.callback(results)
        } finally {
            next?.let(::dispatch)
        }
    }

    private fun takeNextLocked(): Request? {
        if (active != null || pending.isEmpty()) return null
        return pending.removeFirst().also { active = it }
    }
}

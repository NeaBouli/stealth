package com.securecall.app.billing

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Owns one activation attempt across socket replies, send failures and timeouts. */
internal class ActivationRequestSlot {
    data class Request(val id: String, val callback: (Boolean, String, String) -> Unit)
    private val pending = AtomicReference<Request?>(null)

    fun begin(callback: (Boolean, String, String) -> Unit): Request? {
        val request = Request(UUID.randomUUID().toString(), callback)
        return if (pending.compareAndSet(null, request)) request else null
    }

    fun take(id: String): Request? {
        val request = pending.get() ?: return null
        return if (request.id == id && pending.compareAndSet(request, null)) request else null
    }

    fun clear(): Request? = pending.getAndSet(null)
}

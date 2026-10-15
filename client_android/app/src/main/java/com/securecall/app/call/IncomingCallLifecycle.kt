package com.securecall.app.call

/** One authenticated invite, including the asynchronous Telecom binding window. */
internal class IncomingCallLifecycle {
    private enum class State { PENDING, READY, RINGING, ACTIVE, ENDED }
    private var state = State.PENDING

    fun attach(): Boolean {
        if (state != State.PENDING) return false
        state = State.READY
        return true
    }

    fun showUi(): Boolean {
        if (state != State.READY) return false
        state = State.RINGING
        return true
    }

    fun answer(): Boolean {
        if (state != State.READY && state != State.RINGING) return false
        state = State.ACTIVE
        return true
    }

    fun end(): Boolean {
        if (state == State.ENDED) return false
        state = State.ENDED
        return true
    }

    fun shouldTimeout(): Boolean = state != State.ACTIVE && state != State.ENDED
    fun isActive(): Boolean = state == State.ACTIVE
}

internal object IncomingCallAlertPolicy {
    // NotificationCompat's silent grouping can suppress full-screen intents.
    // Telecom notifications must alert themselves, without a second service ringtone.
    fun serviceHandlesRingtone(telecomManaged: Boolean, serviceAvailable: Boolean): Boolean =
        !telecomManaged && serviceAvailable
}

/** Suppress delayed FCM/WS deliveries after hangup without retaining an unbounded history. */
internal class EndedIncomingCalls(private val capacity: Int = 64) {
    private val sessions = linkedSetOf<String>()

    fun remember(sessionId: String) {
        if (sessionId.isEmpty()) return
        sessions.add(sessionId)
        if (sessions.size > capacity) sessions.remove(sessions.first())
    }

    fun contains(sessionId: String): Boolean = sessionId in sessions
}

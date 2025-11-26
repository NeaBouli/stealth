package com.securecall.app.call

/**
 * PATCH 203:
 * Einfache State Machine für den Call-Zustand.
 */
enum class CallState {
    IDLE,
    RINGING,
    OUTGOING,
    ACTIVE,
    ENDED
}

package com.securecall.app.ghostnet.call

/**
 * PATCH 229:
 * Einfacher Zustandsautomat für einen GhostCall.
 * Später kann das erweitert werden (RINGING, RECONNECTING, etc.).
 */
enum class GhostCallState {
    IDLE,
    ESTABLISHING,
    ACTIVE,
    TERMINATING,
    ENDED
}

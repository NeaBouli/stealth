package com.securecall.app.ghostnet.session

/**
 * PATCH 233:
 * GhostNet Session State Skeleton.
 * Wird später durch Transport/Crypto/Signaling gesteuert.
 */
enum class GhostNetSessionState {
    IDLE,
    NEGOTIATING,
    ACTIVE,
    TERMINATING,
    DEAD
}

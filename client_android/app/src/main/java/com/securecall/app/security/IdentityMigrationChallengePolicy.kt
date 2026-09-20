package com.securecall.app.security

internal object IdentityMigrationChallengePolicy {
    enum class Action { SUBMIT, RESTART_REGISTRATION, REJECT }

    fun decide(
        isConnected: Boolean,
        registrationPending: Boolean,
        requestedClientId: String?,
        challengeClientId: String,
    ): Action {
        if (requestedClientId != null && requestedClientId != challengeClientId) {
            return Action.REJECT
        }
        if (!isConnected || !registrationPending || requestedClientId == null) {
            return Action.RESTART_REGISTRATION
        }
        return Action.SUBMIT
    }
}

package com.securecall.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityMigrationChallengePolicyTest {
    @Test
    fun connectedPendingRegistrationSubmitsMatchingChallenge() {
        assertEquals(
            IdentityMigrationChallengePolicy.Action.SUBMIT,
            IdentityMigrationChallengePolicy.decide(true, true, "legacy-01", "legacy-01"),
        )
    }

    @Test
    fun coldOrDisconnectedServiceRequestsFreshRegistration() {
        assertEquals(
            IdentityMigrationChallengePolicy.Action.RESTART_REGISTRATION,
            IdentityMigrationChallengePolicy.decide(false, false, null, "legacy-01"),
        )
        assertEquals(
            IdentityMigrationChallengePolicy.Action.RESTART_REGISTRATION,
            IdentityMigrationChallengePolicy.decide(true, false, "legacy-01", "legacy-01"),
        )
    }

    @Test
    fun challengeForDifferentRegistrationIsRejected() {
        assertEquals(
            IdentityMigrationChallengePolicy.Action.REJECT,
            IdentityMigrationChallengePolicy.decide(true, true, "legacy-02", "legacy-01"),
        )
    }
}

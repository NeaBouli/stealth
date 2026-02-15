package com.securecall.app.security

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SecurityEnforcer.
 *
 * Since FeatureFlags is a compile-time object per flavor,
 * these tests validate the evaluate() logic with the current
 * flavor's configuration.
 */
class SecurityEnforcerTest {

    @Test
    fun `integrity check is always evaluated`() {
        val action = SecurityEnforcer.evaluate(
            SecurityEnforcer.Violation.INTEGRITY_CHECK_FAILED
        )
        // Should never be ALLOW for integrity checks
        assertNotEquals(SecurityEnforcer.Action.ALLOW, action)
    }

    @Test
    fun `evaluate returns consistent action for same violation`() {
        val first = SecurityEnforcer.evaluate(SecurityEnforcer.Violation.ROOT_DETECTED)
        val second = SecurityEnforcer.evaluate(SecurityEnforcer.Violation.ROOT_DETECTED)
        assertEquals(first, second)
    }

    @Test
    fun `all violation types can be evaluated without exception`() {
        for (violation in SecurityEnforcer.Violation.values()) {
            // Should not throw
            val action = SecurityEnforcer.evaluate(violation)
            assertNotNull(action)
        }
    }

    @Test
    fun `handle returns same action as evaluate`() {
        for (violation in SecurityEnforcer.Violation.values()) {
            val expected = SecurityEnforcer.evaluate(violation)
            if (expected != SecurityEnforcer.Action.TERMINATE) {
                // Only test non-terminate to avoid killing the test process
                val actual = SecurityEnforcer.handle(violation)
                assertEquals(expected, actual)
            }
        }
    }
}

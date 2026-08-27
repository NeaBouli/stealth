package com.securecall.app.config

/**
 * Centralized Free-tier limit policy.
 *
 * Single source of truth for how [FeatureProvider.maxContacts] and
 * [FeatureProvider.maxCallDurationMinutes] are enforced at runtime.
 * Pure Kotlin with no Android dependencies so the rules are unit-testable
 * on the JVM.
 *
 * Convention (defined by [FeatureProvider]): 0 means unlimited.
 * Negative values are invalid and are treated as unlimited, so a broken
 * configuration can never hard-block paid users.
 */
object TierLimitPolicy {

    /** True if adding a brand-new contact is allowed. Updates/removals are never limited. */
    fun canAddContact(currentCount: Int, maxContacts: Int): Boolean {
        if (maxContacts <= 0) return true // 0 = unlimited; negative = invalid → unlimited
        return currentCount.coerceAtLeast(0) < maxContacts
    }

    /** Call duration limit in milliseconds; 0 means no limit. */
    fun callDurationLimitMs(maxCallDurationMinutes: Int): Long {
        if (maxCallDurationMinutes <= 0) return 0L // 0 = unlimited; negative = invalid → unlimited
        return maxCallDurationMinutes.toLong() * 60_000L
    }

    /** Deterministic user-facing explanation when the contact limit rejects an add. */
    fun contactLimitMessage(maxContacts: Int): String =
        "Contact limit reached ($maxContacts on Free). Upgrade to Pro for unlimited contacts."

    /** Deterministic user-facing explanation when a call is auto-ended at the limit. */
    fun callLimitMessage(maxCallDurationMinutes: Int): String =
        "Free calls are limited to $maxCallDurationMinutes minutes. Upgrade to Pro for unlimited calls."
}

package com.securecall.app.billing

import android.content.Context
import android.util.Log

/**
 * Checks subscription validity at app startup.
 *
 * - If tier is FREE, always passes.
 * - If subscription is active (expiresAt in future), passes.
 * - If expired < 7 days ago, grants grace period with warning.
 * - If expired > 7 days ago, downgrades to FREE.
 */
object LicenseChecker {

    private const val TAG = "LicenseChecker"
    private const val GRACE_PERIOD_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

    fun checkAtStartup(context: Context): SubscriptionTier {
        val manager = SubscriptionManager(context)
        val tier = manager.getCurrentTier()

        if (tier == SubscriptionTier.FREE) {
            Log.d(TAG, "Tier is FREE — no license check needed")
            return SubscriptionTier.FREE
        }

        val expiresAt = manager.getExpiresAt()
        val now = System.currentTimeMillis()

        // No expiry set (lifetime or not yet verified)
        if (expiresAt == 0L) {
            Log.d(TAG, "Tier=$tier with no expiry — keeping active")
            return tier
        }

        // Still active
        if (now < expiresAt) {
            Log.d(TAG, "Tier=$tier — subscription active (expires in ${(expiresAt - now) / 1000}s)")
            return tier
        }

        // Expired — check grace period
        val expiredDuration = now - expiresAt
        if (expiredDuration < GRACE_PERIOD_MS) {
            val daysLeft = (GRACE_PERIOD_MS - expiredDuration) / (24 * 60 * 60 * 1000)
            Log.w(TAG, "Tier=$tier — subscription expired but within grace period (${daysLeft}d left)")
            return tier
        }

        // Grace period exceeded — downgrade
        Log.w(TAG, "Tier=$tier — subscription expired beyond grace period — downgrading to FREE")
        manager.clearSubscription()
        return SubscriptionTier.FREE
    }
}

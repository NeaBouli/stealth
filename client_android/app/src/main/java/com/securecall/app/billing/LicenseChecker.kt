package com.securecall.app.billing

import android.content.Context
import android.util.Log

/**
 * Checks subscription validity at app startup.
 *
 * - If tier is FREE, always passes.
 * - Only a currently active, server-verified subscription passes.
 * - Missing verification or any expiry downgrades immediately to FREE.
 */
object LicenseChecker {

    private const val TAG = "LicenseChecker"
    fun checkAtStartup(context: Context): SubscriptionTier {
        val manager = SubscriptionManager(context)
        val tier = manager.getCurrentTier()

        if (tier == SubscriptionTier.FREE) {
            Log.d(TAG, "Tier is FREE — no license check needed")
            return SubscriptionTier.FREE
        }

        val expiresAt = manager.getExpiresAt()
        val now = System.currentTimeMillis()

        if (manager.isSubscriptionActive() && expiresAt > now) {
            Log.d(TAG, "Tier=$tier — subscription active (expires in ${(expiresAt - now) / 1000}s)")
            return tier
        }

        Log.w(TAG, "Tier=$tier — entitlement is not currently verified — downgrading to FREE")
        manager.clearSubscription()
        com.securecall.app.config.TierManager.applyTier(context)
        return SubscriptionTier.FREE
    }
}

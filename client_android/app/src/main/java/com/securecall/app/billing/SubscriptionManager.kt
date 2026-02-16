package com.securecall.app.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Persists subscription state in SharedPreferences.
 *
 * Stores: tier, purchaseToken, expiresAt, productId, lastVerifiedAt.
 */
class SubscriptionManager(context: Context) {

    companion object {
        private const val TAG = "SubscriptionManager"
        private const val PREFS_NAME = "securecall_subscription"
        private const val KEY_TIER = "tier"
        private const val KEY_PURCHASE_TOKEN = "purchase_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_PRODUCT_ID = "product_id"
        private const val KEY_LAST_VERIFIED_AT = "last_verified_at"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCurrentTier(): SubscriptionTier {
        val name = prefs.getString(KEY_TIER, SubscriptionTier.FREE.name)
            ?: SubscriptionTier.FREE.name
        return SubscriptionTier.fromName(name)
    }

    fun getPurchaseToken(): String {
        return prefs.getString(KEY_PURCHASE_TOKEN, "") ?: ""
    }

    fun getExpiresAt(): Long {
        return prefs.getLong(KEY_EXPIRES_AT, 0L)
    }

    fun getProductId(): String {
        return prefs.getString(KEY_PRODUCT_ID, "") ?: ""
    }

    fun getLastVerifiedAt(): Long {
        return prefs.getLong(KEY_LAST_VERIFIED_AT, 0L)
    }

    fun updateSubscription(
        tier: SubscriptionTier,
        purchaseToken: String,
        expiresAt: Long,
        productId: String
    ) {
        prefs.edit()
            .putString(KEY_TIER, tier.name)
            .putString(KEY_PURCHASE_TOKEN, purchaseToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_PRODUCT_ID, productId)
            .putLong(KEY_LAST_VERIFIED_AT, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Subscription updated: tier=${tier.name}, productId=$productId")
    }

    fun updateFromServerVerification(tier: SubscriptionTier, expiresAt: Long) {
        prefs.edit()
            .putString(KEY_TIER, tier.name)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putLong(KEY_LAST_VERIFIED_AT, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Server verification updated: tier=${tier.name}, expiresAt=$expiresAt")
    }

    fun clearSubscription() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Subscription cleared — downgraded to FREE")
    }

    fun isSubscriptionActive(): Boolean {
        val tier = getCurrentTier()
        if (tier == SubscriptionTier.FREE) return true // FREE is always active
        val expiresAt = getExpiresAt()
        return expiresAt == 0L || System.currentTimeMillis() < expiresAt
    }
}

package com.securecall.app.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.securecall.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

        private val verifyHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
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

    /**
     * Fix CLIENT-CRIT-002 (2026-04-16): ask the signaling backend whether our
     * subscription is still valid. Handles the case where the server cancels a
     * subscription (chargeback, refund, manual cancel in Play Console) while
     * the app was offline, so the client never received a push notification.
     *
     * Call on a background thread. On receiving `{valid: false}` we immediately
     * downgrade local state to FREE so paid features stop working. On a network
     * error the stored state is left alone (fail-open — better UX than
     * kicking paying users out because their carrier dropped a packet).
     */
    fun verifyAgainstServer(clientId: String) {
        val currentTier = getCurrentTier()
        if (currentTier == SubscriptionTier.FREE) {
            // Nothing to verify — free tier always valid.
            return
        }
        val baseUrl = BuildConfig.SIGNAL_WS_URL
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .replace("/signal", "")
        val url = "$baseUrl/subscription/status"
        val purchaseToken = getPurchaseToken()
        val body = JSONObject().apply {
            put("clientId", clientId)
            if (purchaseToken.isNotEmpty()) put("purchaseToken", purchaseToken)
        }.toString()

        try {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val response = verifyHttpClient.newCall(request).execute()
            val raw = response.body?.string()
            response.close()
            if (!response.isSuccessful || raw.isNullOrEmpty()) {
                Log.w(TAG, "verifyAgainstServer: HTTP ${response.code} — keeping local state")
                return
            }
            val json = JSONObject(raw)
            val valid = json.optBoolean("valid", false)
            prefs.edit().putLong(KEY_LAST_VERIFIED_AT, System.currentTimeMillis()).apply()
            if (valid) {
                val tierName = json.optString("tier", currentTier.name)
                val expiresAt = json.optLong("expiresAt", getExpiresAt())
                updateFromServerVerification(SubscriptionTier.fromName(tierName), expiresAt)
            } else {
                val reason = json.optString("reason", "unknown")
                Log.w(TAG, "verifyAgainstServer: server says not valid (reason=$reason) — downgrading to FREE")
                clearSubscription()
            }
        } catch (e: Exception) {
            // Network / parse error — keep local state (fail-open).
            Log.w(TAG, "verifyAgainstServer: network error: ${e.message}")
        }
    }

}

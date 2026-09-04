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
import java.util.UUID

/**
 * Stores only server-authoritative Google Play subscription state.
 *
 * A Play purchase is pending and grants no access until the signaling backend
 * verifies the exact package, product, request and catalog version.
 */
class SubscriptionManager(context: Context) {

    companion object {
        private const val TAG = "SubscriptionManager"
        private const val PREFS_NAME = "securecall_subscription"
        private const val KEY_TIER = "tier"
        private const val KEY_PURCHASE_TOKEN = "purchase_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_PRODUCT_ID = "product_id"
        private const val KEY_PACKAGE_NAME = "package_name"
        private const val KEY_CATALOG_VERSION = "catalog_version"
        private const val KEY_VERIFIED = "server_verified"
        private const val KEY_PENDING_REQUEST_ID = "pending_request_id"
        private const val KEY_LAST_VERIFIED_AT = "last_verified_at"

        private val verifyHttpClient: OkHttpClient by lazy {
            com.securecall.app.net.NetworkManager.buildPinnedClient(connectTimeoutSec = 10, readTimeoutSec = 10)
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appPackageName = context.packageName

    fun getCurrentTier(): SubscriptionTier {
        val stored = SubscriptionTier.fromName(
            prefs.getString(KEY_TIER, SubscriptionTier.FREE.name)
                ?: SubscriptionTier.FREE.name
        )
        return if (stored == SubscriptionTier.FREE || isVerifiedEntitlementActive()) {
            stored
        } else {
            SubscriptionTier.FREE
        }
    }

    fun getPurchaseToken(): String = prefs.getString(KEY_PURCHASE_TOKEN, "") ?: ""

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun getProductId(): String = prefs.getString(KEY_PRODUCT_ID, "") ?: ""

    fun getLastVerifiedAt(): Long = prefs.getLong(KEY_LAST_VERIFIED_AT, 0L)

    fun recordPendingPurchase(purchaseToken: String, productId: String): String {
        require(purchaseToken.isNotBlank()) { "purchase_token_required" }
        require(productId.isNotBlank()) { "product_id_required" }
        val requestId = UUID.randomUUID().toString()
        val committed = prefs.edit()
            .putString(KEY_TIER, SubscriptionTier.FREE.name)
            .putString(KEY_PURCHASE_TOKEN, purchaseToken)
            .putLong(KEY_EXPIRES_AT, 0L)
            .putString(KEY_PRODUCT_ID, productId)
            .putString(KEY_PACKAGE_NAME, appPackageName)
            .putString(KEY_CATALOG_VERSION, BuildConfig.PLAY_CATALOG_VERSION)
            .putBoolean(KEY_VERIFIED, false)
            .putString(KEY_PENDING_REQUEST_ID, requestId)
            .putLong(KEY_LAST_VERIFIED_AT, 0L)
            .commit()
        check(committed) { "pending_purchase_persistence_failed" }
        Log.d(TAG, "Purchase pending server verification: productId=" + productId)
        return requestId
    }

    fun applyServerVerification(
        requestId: String,
        tier: SubscriptionTier,
        expiresAt: Long,
        productId: String,
        packageName: String,
        catalogVersion: String
    ): Boolean {
        val matchesPending = requestId.isNotBlank()
            && requestId == prefs.getString(KEY_PENDING_REQUEST_ID, null)
            && productId == getProductId()
            && packageName == appPackageName
            && packageName == prefs.getString(KEY_PACKAGE_NAME, null)
            && catalogVersion == BuildConfig.PLAY_CATALOG_VERSION
            && catalogVersion == prefs.getString(KEY_CATALOG_VERSION, null)
        if (!matchesPending || tier == SubscriptionTier.FREE || expiresAt <= System.currentTimeMillis()) {
            clearSubscription()
            return false
        }
        val committed = prefs.edit()
            .putString(KEY_TIER, tier.name)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putBoolean(KEY_VERIFIED, true)
            .remove(KEY_PENDING_REQUEST_ID)
            .putLong(KEY_LAST_VERIFIED_AT, System.currentTimeMillis())
            .commit()
        if (!committed) {
            clearSubscription()
            return false
        }
        Log.d(TAG, "Server verification accepted: tier=" + tier.name + ", productId=" + productId)
        return true
    }

    fun clearSubscription() {
        prefs.edit().clear().commit()
        Log.d(TAG, "Subscription cleared - downgraded to FREE")
    }

    fun isSubscriptionActive(): Boolean {
        val tier = getCurrentTier()
        return tier != SubscriptionTier.FREE && isVerifiedEntitlementActive()
    }

    private fun isVerifiedEntitlementActive(): Boolean {
        return prefs.getBoolean(KEY_VERIFIED, false)
            && prefs.getString(KEY_PACKAGE_NAME, null) == appPackageName
            && prefs.getString(KEY_CATALOG_VERSION, null) == BuildConfig.PLAY_CATALOG_VERSION
            && getPurchaseToken().isNotBlank()
            && getProductId().isNotBlank()
            && getExpiresAt() > System.currentTimeMillis()
    }

    /**
     * Revalidates paid access against the authoritative server.
     * Any unavailable, malformed or mismatched response closes local access.
     */
    fun verifyAgainstServer(clientId: String) {
        if (getCurrentTier() == SubscriptionTier.FREE) return
        val baseUrl = BuildConfig.SIGNAL_WS_URL
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .replace("/signal", "")
        val body = JSONObject().apply {
            put("clientId", clientId)
            put("purchaseToken", getPurchaseToken())
            put("packageName", appPackageName)
            put("catalogVersion", BuildConfig.PLAY_CATALOG_VERSION)
        }.toString()

        try {
            val request = Request.Builder()
                .url(baseUrl + "/subscription/status")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            verifyHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrEmpty()) {
                    Log.w(TAG, "Authoritative subscription status unavailable")
                    clearSubscription()
                    return
                }
                val json = JSONObject(raw)
                val tier = SubscriptionTier.fromName(json.optString("tier"))
                val accepted = json.optBoolean("valid", false)
                    && tier != SubscriptionTier.FREE
                    && json.optString("productId") == getProductId()
                    && json.optString("packageName") == appPackageName
                    && json.optString("catalogVersion") == BuildConfig.PLAY_CATALOG_VERSION
                    && json.optLong("expiresAt", 0L) > System.currentTimeMillis()
                if (!accepted) {
                    clearSubscription()
                    return
                }
                val committed = prefs.edit()
                    .putString(KEY_TIER, tier.name)
                    .putLong(KEY_EXPIRES_AT, json.getLong("expiresAt"))
                    .putBoolean(KEY_VERIFIED, true)
                    .putLong(KEY_LAST_VERIFIED_AT, System.currentTimeMillis())
                    .commit()
                if (!committed) clearSubscription()
            }
        } catch (_: Exception) {
            Log.w(TAG, "Authoritative subscription check failed")
            clearSubscription()
        }
    }
}

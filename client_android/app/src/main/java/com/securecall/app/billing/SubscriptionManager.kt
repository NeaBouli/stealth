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
 * A Play purchase is a pending receipt and grants no access until the signaling backend
 * verifies the exact request, package, product and catalog version. Pending receipts are
 * stored apart from the current verified entitlement: recording a receipt, or a stale,
 * unrelated or failed answer, never clears or downgrades a valid verified right before
 * its finite expiry.
 *
 * Several receipts (for example a SUBS and an INAPP restore) resolve deterministically,
 * independent of answer order: nothing is applied while any receipt is still unanswered,
 * exactly one verified purchase is applied, and two or more distinct verified purchases
 * return [VerificationOutcome.COEXISTENCE_POLICY_REQUIRED]. Selecting between coexisting
 * purchases is an approved commercial input that does not exist yet; neither record is
 * cancelled and no rank or upgrade order is assumed.
 */
class SubscriptionManager @JvmOverloads constructor(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    enum class VerificationOutcome {
        ACCEPTED,
        STALE_IGNORED,
        REJECTED,
        PERSISTENCE_FAILED,
        AWAITING_OTHER_RECEIPTS,
        COEXISTENCE_POLICY_REQUIRED
    }

    /** Verified-entitlement snapshot that an HTTP status answer is bound to. */
    data class StatusSnapshot(val purchaseToken: String, val productId: String, val generation: Long)

    private data class Receipt(
        val requestId: String,
        val purchaseToken: String,
        val productId: String,
        val packageName: String,
        val catalogVersion: String,
        val createdAt: Long,
        val verifiedTier: SubscriptionTier?,
        val verifiedExpiresAt: Long
    )

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
        private const val KEY_LEGACY_PENDING_REQUEST_ID = "pending_request_id"
        private const val KEY_LAST_VERIFIED_AT = "last_verified_at"
        private const val KEY_GENERATION = "verified_generation"
        private const val KEY_RECEIPT_IDS = "pending_receipt_ids"
        private const val RECEIPT_PREFIX = "pending_receipt."
        private val RECEIPT_FIELDS = listOf("token", "product", "package", "catalog", "created", "tier", "expires")
        private const val MAX_RECEIPTS = 6
        private val REQUEST_ID = Regex("^[a-f0-9-]{36}$", RegexOption.IGNORE_CASE)

        // All instances share one SharedPreferences file; serialize read-modify-write.
        private val LOCK = Any()

        private val verifyHttpClient: OkHttpClient by lazy {
            com.securecall.app.net.NetworkManager.buildPinnedClient(connectTimeoutSec = 10, readTimeoutSec = 10)
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appPackageName = context.packageName

    fun getCurrentTier(): SubscriptionTier = synchronized(LOCK) {
        val stored = SubscriptionTier.fromName(
            prefs.getString(KEY_TIER, SubscriptionTier.FREE.name)
                ?: SubscriptionTier.FREE.name
        )
        if (stored == SubscriptionTier.FREE || isVerifiedEntitlementActive()) {
            stored
        } else {
            SubscriptionTier.FREE
        }
    }

    fun getPurchaseToken(): String = prefs.getString(KEY_PURCHASE_TOKEN, "") ?: ""

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun getProductId(): String = prefs.getString(KEY_PRODUCT_ID, "") ?: ""

    fun getLastVerifiedAt(): Long = prefs.getLong(KEY_LAST_VERIFIED_AT, 0L)

    fun pendingReceiptCount(): Int = synchronized(LOCK) { loadReceipts().size }

    /**
     * Records a pending receipt without touching the verified entitlement. Re-recording the
     * same purchase token replaces its older request, so that request's answer becomes stale.
     */
    fun recordPendingPurchase(purchaseToken: String, productId: String): String = synchronized(LOCK) {
        require(purchaseToken.isNotBlank()) { "purchase_token_required" }
        require(productId.isNotBlank()) { "product_id_required" }
        val now = clock()
        val receipts = loadReceipts()
        require(receipts.none { it.purchaseToken == purchaseToken && it.productId != productId }) {
            "purchase_product_mismatch"
        }
        val kept = receipts.filter { it.purchaseToken != purchaseToken || it.verifiedTier != null }
        check(kept.size < MAX_RECEIPTS) { "pending_purchase_limit" }
        val receipt = Receipt(
            requestId = UUID.randomUUID().toString(),
            purchaseToken = purchaseToken,
            productId = productId,
            packageName = appPackageName,
            catalogVersion = BuildConfig.PLAY_CATALOG_VERSION,
            createdAt = now,
            verifiedTier = null,
            verifiedExpiresAt = 0L
        )
        check(commitDurably(writeReceipts(prefs.edit(), kept + receipt))) { "pending_purchase_persistence_failed" }
        Log.d(TAG, "Purchase pending server verification: productId=" + productId)
        receipt.requestId
    }

    /** Drops one pending receipt (for example when it could not be sent); verified state stays. */
    fun discardPendingPurchase(requestId: String) {
        synchronized(LOCK) {
            val receipts = loadReceipts()
            if (receipts.none { it.requestId == requestId }) return
            commitDurably(writeReceipts(prefs.edit(), receipts.filter {
                it.requestId != requestId || it.verifiedTier != null
            }))
        }
    }

    /** Compatibility entry point for a successful answer; true only when it was applied. */
    fun applyServerVerification(
        requestId: String,
        tier: SubscriptionTier,
        expiresAt: Long,
        productId: String,
        packageName: String,
        catalogVersion: String
    ): Boolean = onVerificationAck(
        success = true,
        requestId = requestId,
        tier = tier,
        expiresAt = expiresAt,
        productId = productId,
        packageName = packageName,
        catalogVersion = catalogVersion
    ) == VerificationOutcome.ACCEPTED

    /**
     * Applies one SUBSCRIPTION_VERIFY_ACK. Only an answer for a currently pending request can
     * change state; a matching failed answer drops just that receipt. The verified
     * entitlement is replaced only by the deterministic resolution described on the class.
     */
    fun onVerificationAck(
        success: Boolean,
        requestId: String,
        tier: SubscriptionTier,
        expiresAt: Long,
        productId: String,
        packageName: String,
        catalogVersion: String
    ): VerificationOutcome = synchronized(LOCK) {
        val now = clock()
        val receipts = loadReceipts()
        val receipt = receipts.firstOrNull { it.requestId == requestId && requestId.isNotBlank() }
            ?: return@synchronized VerificationOutcome.STALE_IGNORED
        val matches = productId == receipt.productId
            && packageName == appPackageName
            && packageName == receipt.packageName
            && catalogVersion == BuildConfig.PLAY_CATALOG_VERSION
            && catalogVersion == receipt.catalogVersion
        if (!matches && (success || productId.isNotBlank() || packageName.isNotBlank() || catalogVersion.isNotBlank())) {
            return@synchronized VerificationOutcome.STALE_IGNORED
        }
        // A finalized receipt is immutable. Only an identical success may retry a failed
        // final grant write; a duplicate failure cannot cancel a verified restore candidate.
        if (receipt.verifiedTier != null) {
            return@synchronized if (success && matches && tier == receipt.verifiedTier
                && expiresAt == receipt.verifiedExpiresAt) resolveReceipts(now)
                else VerificationOutcome.STALE_IGNORED
        }
        val others = receipts.filter { it.requestId != requestId }
        if (!success || !matches || tier == SubscriptionTier.FREE || expiresAt <= now) {
            if (!commitDurably(writeReceipts(prefs.edit(), others))) {
                return@synchronized VerificationOutcome.PERSISTENCE_FAILED
            }
            val resolved = resolveReceipts(now)
            return@synchronized if (resolved == VerificationOutcome.ACCEPTED
                || resolved == VerificationOutcome.COEXISTENCE_POLICY_REQUIRED
                || resolved == VerificationOutcome.PERSISTENCE_FAILED) resolved
                else VerificationOutcome.REJECTED
        }
        val verified = receipt.copy(verifiedTier = tier, verifiedExpiresAt = expiresAt)
        if (!commitDurably(writeReceipts(prefs.edit(), others.filter {
                it.purchaseToken != receipt.purchaseToken
            } + verified))) {
            return@synchronized VerificationOutcome.PERSISTENCE_FAILED
        }
        resolveReceipts(now)
    }

    /** Full local reset (explicit sign-out style); not used for stale or failed answers. */
    fun clearSubscription() {
        synchronized(LOCK) {
            commitDurably(prefs.edit().clear()
                .putLong(KEY_GENERATION, prefs.getLong(KEY_GENERATION, 0L) + 1))
        }
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
            && getExpiresAt() > clock()
    }

    /** Snapshot of the active verified entitlement, or null when nothing paid is active. */
    fun statusSnapshot(): StatusSnapshot? = synchronized(LOCK) {
        if (getCurrentTier() == SubscriptionTier.FREE) null
        else StatusSnapshot(getPurchaseToken(), getProductId(), prefs.getLong(KEY_GENERATION, 0L))
    }

    /**
     * Revalidates paid access against the authoritative server.
     * Any unavailable, malformed or mismatched response closes the verified entitlement the
     * request was made for, but never a newer one accepted while the request was in flight.
     */
    fun verifyAgainstServer(clientId: String) {
        val snapshot = statusSnapshot() ?: return
        val baseUrl = BuildConfig.SIGNAL_WS_URL
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .replace("/signal", "")
        val body = JSONObject().apply {
            put("clientId", clientId)
            put("purchaseToken", snapshot.purchaseToken)
            put("packageName", appPackageName)
            put("catalogVersion", BuildConfig.PLAY_CATALOG_VERSION)
        }.toString()

        try {
            val request = Request.Builder()
                .url(baseUrl + "/subscription/status")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            verifyHttpClient.newCall(request).execute().use { response ->
                applyStatusResponse(snapshot, response.isSuccessful, response.body?.string())
            }
        } catch (_: Exception) {
            Log.w(TAG, "Authoritative subscription check failed")
            applyStatusResponse(snapshot, false, null)
        }
    }

    /** Applies an HTTP status answer only if the verified entitlement is still [snapshot]. */
    fun applyStatusResponse(snapshot: StatusSnapshot, httpOk: Boolean, raw: String?): Boolean = synchronized(LOCK) {
        if (getPurchaseToken() != snapshot.purchaseToken || getProductId() != snapshot.productId
            || prefs.getLong(KEY_GENERATION, 0L) != snapshot.generation) {
            Log.d(TAG, "Ignoring status answer for a superseded entitlement")
            return@synchronized false
        }
        val json = if (httpOk && !raw.isNullOrEmpty()) runCatching { JSONObject(raw) }.getOrNull() else null
        val now = clock()
        val tier = SubscriptionTier.fromName(json?.optString("tier") ?: "")
        val expiresAt = json?.optLong("expiresAt", 0L) ?: 0L
        val accepted = json != null
            && json.optBoolean("valid", false)
            && tier != SubscriptionTier.FREE
            && json.optString("productId") == snapshot.productId
            && json.optString("packageName") == appPackageName
            && json.optString("catalogVersion") == BuildConfig.PLAY_CATALOG_VERSION
            && expiresAt > now
        if (accepted) {
            return@synchronized commitDurably(
                prefs.edit()
                .putString(KEY_TIER, tier.name)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .putBoolean(KEY_VERIFIED, true)
                .putLong(KEY_LAST_VERIFIED_AT, now)
                .putLong(KEY_GENERATION, snapshot.generation + 1)
            )
        }
        Log.w(TAG, "Authoritative subscription status unavailable or invalid")
        // Do not resurrect this token from a queued verified receipt, nor select a
        // different purchase as a side effect of a failed status request.
        commitDurably(writeReceipts(clearVerifiedEntitlement(prefs.edit()),
            loadReceipts().filter { it.purchaseToken != snapshot.purchaseToken }))
        false
    }

    // ─── Receipt resolution (caller holds LOCK) ─────────────────

    private fun resolveReceipts(now: Long): VerificationOutcome {
        val live = loadReceipts()
        if (live.any { it.verifiedTier == null }) return VerificationOutcome.AWAITING_OTHER_RECEIPTS
        val verified = live.filter { it.verifiedTier != null && it.verifiedExpiresAt > now }
        val activeToken = if (isVerifiedEntitlementActive()) getPurchaseToken() else null
        val distinctTokens = (verified.map { it.purchaseToken } + listOfNotNull(activeToken)).toSet()
        if (verified.isEmpty()) {
            commitDurably(writeReceipts(prefs.edit(), emptyList()))
            return VerificationOutcome.REJECTED
        }
        if (distinctTokens.size > 1) {
            Log.w(TAG, "Coexisting verified purchases need an approved selection policy; none applied")
            return VerificationOutcome.COEXISTENCE_POLICY_REQUIRED
        }
        // One distinct purchase: a new grant or a refresh of the active one.
        val chosen = verified.maxByOrNull { it.createdAt } ?: return VerificationOutcome.REJECTED
        val tier = chosen.verifiedTier ?: return VerificationOutcome.REJECTED
        val editor = writeReceipts(prefs.edit(), emptyList())
            .putString(KEY_TIER, tier.name)
            .putString(KEY_PURCHASE_TOKEN, chosen.purchaseToken)
            .putLong(KEY_EXPIRES_AT, chosen.verifiedExpiresAt)
            .putString(KEY_PRODUCT_ID, chosen.productId)
            .putString(KEY_PACKAGE_NAME, chosen.packageName)
            .putString(KEY_CATALOG_VERSION, chosen.catalogVersion)
            .putBoolean(KEY_VERIFIED, true)
            .putLong(KEY_LAST_VERIFIED_AT, now)
            .putLong(KEY_GENERATION, prefs.getLong(KEY_GENERATION, 0L) + 1)
        if (!commitDurably(editor)) return VerificationOutcome.PERSISTENCE_FAILED
        Log.d(TAG, "Server verification accepted: tier=" + tier.name + ", productId=" + chosen.productId)
        return VerificationOutcome.ACCEPTED
    }

    // Android commits update the shared in-memory map even when the disk write fails.
    // Restore that view before returning failure, so another manager cannot grant from it.
    private fun commitDurably(editor: SharedPreferences.Editor): Boolean {
        val before = prefs.all
        if (editor.commit()) return true
        val rollback = prefs.edit().clear()
        for ((key, value) in before) {
            when (value) {
                is String -> rollback.putString(key, value)
                is Long -> rollback.putLong(key, value)
                is Int -> rollback.putInt(key, value)
                is Boolean -> rollback.putBoolean(key, value)
                is Float -> rollback.putFloat(key, value)
                is Set<*> -> rollback.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        rollback.commit()
        return false
    }

    private fun clearVerifiedEntitlement(editor: SharedPreferences.Editor): SharedPreferences.Editor = editor
        .putString(KEY_TIER, SubscriptionTier.FREE.name)
        .remove(KEY_PURCHASE_TOKEN)
        .putLong(KEY_EXPIRES_AT, 0L)
        .remove(KEY_PRODUCT_ID)
        .remove(KEY_PACKAGE_NAME)
        .remove(KEY_CATALOG_VERSION)
        .putBoolean(KEY_VERIFIED, false)
        .putLong(KEY_LAST_VERIFIED_AT, 0L)
        .putLong(KEY_GENERATION, prefs.getLong(KEY_GENERATION, 0L) + 1)

    private fun loadReceipts(): List<Receipt> {
        val ids = prefs.getStringSet(KEY_RECEIPT_IDS, emptySet())?.toList().orEmpty()
        return ids.filter { REQUEST_ID.matches(it) }.mapNotNull { id ->
            val key = RECEIPT_PREFIX + id + "."
            val token = prefs.getString(key + "token", null)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val product = prefs.getString(key + "product", null)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tierName = prefs.getString(key + "tier", null)
            Receipt(
                requestId = id,
                purchaseToken = token,
                productId = product,
                packageName = prefs.getString(key + "package", null) ?: return@mapNotNull null,
                catalogVersion = prefs.getString(key + "catalog", null) ?: return@mapNotNull null,
                createdAt = prefs.getLong(key + "created", 0L),
                verifiedTier = tierName?.let { SubscriptionTier.fromName(it) }
                    ?.takeIf { it != SubscriptionTier.FREE },
                verifiedExpiresAt = prefs.getLong(key + "expires", 0L)
            )
        }.sortedBy { it.createdAt }
    }

    private fun writeReceipts(editor: SharedPreferences.Editor, receipts: List<Receipt>): SharedPreferences.Editor {
        val previous = prefs.getStringSet(KEY_RECEIPT_IDS, emptySet())?.toList().orEmpty()
        for (id in previous) for (field in RECEIPT_FIELDS) editor.remove(RECEIPT_PREFIX + id + "." + field)
        for (receipt in receipts) {
            val key = RECEIPT_PREFIX + receipt.requestId + "."
            editor.putString(key + "token", receipt.purchaseToken)
                .putString(key + "product", receipt.productId)
                .putString(key + "package", receipt.packageName)
                .putString(key + "catalog", receipt.catalogVersion)
                .putLong(key + "created", receipt.createdAt)
            if (receipt.verifiedTier != null) {
                editor.putString(key + "tier", receipt.verifiedTier.name)
                    .putLong(key + "expires", receipt.verifiedExpiresAt)
            }
        }
        // Pre-isolation installs kept one pending request in the verified slot; it is superseded.
        return editor.remove(KEY_LEGACY_PENDING_REQUEST_ID)
            .putStringSet(KEY_RECEIPT_IDS, receipts.map { it.requestId }.toSet())
    }
}

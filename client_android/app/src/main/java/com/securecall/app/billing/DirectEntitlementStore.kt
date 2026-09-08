package com.securecall.app.billing

import android.content.Context
import android.content.SharedPreferences
import com.securecall.app.BuildConfig
import com.securecall.app.billing.DirectEntitlementVerifier.VerifiedDirectEntitlement
import okio.ByteString.Companion.decodeBase64

/** Stores proof, never a user-provided paid tier. Revalidates on every access. */
class DirectEntitlementStore internal constructor(
    private val prefs: SharedPreferences,
    private val subject: () -> String?,
    private val publicKey: String,
    private val flavor: String,
    private val release: String,
    private val clock: () -> Long
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("securecall_direct_entitlement", Context.MODE_PRIVATE),
        { context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE).getString("client_id", null) },
        BuildConfig.ENTITLEMENT_PUBLIC_KEY, BuildConfig.FLAVOR, BuildConfig.ENTITLEMENT_RELEASE_ID,
        { System.currentTimeMillis() / 1000 }
    )

    private fun verify(token: String): VerifiedDirectEntitlement {
        require(flavor in setOf("pro", "premium"))
        val identity = subject() ?: throw IllegalArgumentException("Missing local identity")
        val key = publicKey.decodeBase64()?.toByteArray()
            ?: throw IllegalArgumentException("Verifier not configured")
        val now = clock()
        require(now + 300 >= prefs.getLong("last_verified_time", 0)) { "Clock rollback" }
        return DirectEntitlementVerifier.verify(
            token, key, identity, flavor.uppercase(), release, now
        )
    }

    fun currentTier(): String {
        return try {
            val token = prefs.getString("token", null) ?: return "FREE"
            val verified = verify(token)
            val now = clock()
            if (now - prefs.getLong("last_verified_time", 0) >= 60 &&
                !prefs.edit().putLong("last_verified_time", now).commit()) {
                "FREE"
            } else verified.tier
        } catch (_: Exception) {
            "FREE"
        }
    }

    fun accept(token: String): Boolean = try {
        verify(token)
        prefs.edit().putString("token", token)
            .putLong("last_verified_time", maxOf(clock(), prefs.getLong("last_verified_time", 0))).commit()
    } catch (_: Exception) {
        false
    }

    // Expired proofs remain available for the server's bounded refresh grace,
    // but currentTier never grants access from an expired proof.
    fun tokenForRefresh(): String? = if (flavor in setOf("pro", "premium")) {
        prefs.getString("token", null)?.takeIf { it.length in 1..4096 }
    } else null

    fun revoke(): Boolean = prefs.edit().remove("token").commit()
}

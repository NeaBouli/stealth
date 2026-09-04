package com.securecall.app.billing

import android.content.Context
import android.content.SharedPreferences
import com.securecall.app.BuildConfig
import com.securecall.app.config.FeatureProviderRegistry
import com.securecall.app.config.TierManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionManagerTest {
    private lateinit var prefs: MemoryPreferences
    private lateinit var securePrefs: MemoryPreferences
    private lateinit var context: Context
    private lateinit var manager: SubscriptionManager

    @Before
    fun setUp() {
        prefs = MemoryPreferences()
        securePrefs = MemoryPreferences()
        context = mock()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.packageName).thenReturn("com.securecall.app.free")
        whenever(context.getSharedPreferences("securecall_subscription", Context.MODE_PRIVATE))
            .thenReturn(prefs)
        whenever(context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE))
            .thenReturn(securePrefs)
        manager = SubscriptionManager(context)
    }

    @Test
    fun pendingPurchaseGrantsNoPaidAccess() {
        manager.recordPendingPurchase("purchase-token", "securecall_pro_monthly")
        assertEquals(SubscriptionTier.FREE, manager.getCurrentTier())
        assertFalse(manager.isSubscriptionActive())
    }

    @Test
    fun matchingAuthoritativeResponseActivatesFiniteEntitlement() {
        val requestId = manager.recordPendingPurchase("purchase-token", "securecall_pro_monthly")
        val accepted = manager.applyServerVerification(
            requestId = requestId,
            tier = SubscriptionTier.PRO,
            expiresAt = System.currentTimeMillis() + 60_000,
            productId = "securecall_pro_monthly",
            packageName = "com.securecall.app.free",
            catalogVersion = "securecall-play-v1"
        )
        assertTrue(accepted)
        assertEquals(SubscriptionTier.PRO, manager.getCurrentTier())
        assertTrue(manager.isSubscriptionActive())
    }

    @Test
    fun mismatchedResponseClearsPendingAndPaidState() {
        val requestId = manager.recordPendingPurchase("purchase-token", "securecall_pro_monthly")
        val accepted = manager.applyServerVerification(
            requestId = requestId,
            tier = SubscriptionTier.PRO,
            expiresAt = System.currentTimeMillis() + 60_000,
            productId = "securecall_premium_monthly",
            packageName = "com.securecall.app.free",
            catalogVersion = "securecall-play-v1"
        )
        assertFalse(accepted)
        assertEquals(SubscriptionTier.FREE, manager.getCurrentTier())
        assertEquals("", manager.getPurchaseToken())
    }

    @Test
    fun expiredAuthoritativeResponseFailsClosed() {
        val requestId = manager.recordPendingPurchase("purchase-token", "securecall_pro_monthly")
        val accepted = manager.applyServerVerification(
            requestId = requestId,
            tier = SubscriptionTier.PRO,
            expiresAt = System.currentTimeMillis() - 1,
            productId = "securecall_pro_monthly",
            packageName = "com.securecall.app.free",
            catalogVersion = "securecall-play-v1"
        )
        assertFalse(accepted)
        assertEquals(SubscriptionTier.FREE, manager.getCurrentTier())
    }

    @Test
    fun disabledLegacyActivationCannotGrantRuntimeTier() {
        securePrefs.edit().putString("activated_tier", "premium").commit()
        TierManager.applyTier(context)

        val buildTier = BuildConfig.FLAVOR.uppercase()
        assertEquals(buildTier, TierManager.getCurrentTier(context))
        assertEquals(buildTier, FeatureProviderRegistry.get().tier)
        assertFalse(securePrefs.contains("activated_tier"))
    }

    @Test
    fun clearedEntitlementCanReplacePaidRuntimeProvider() {
        val requestId = manager.recordPendingPurchase("purchase-token", "securecall_pro_monthly")
        assertTrue(manager.applyServerVerification(
            requestId = requestId,
            tier = SubscriptionTier.PRO,
            expiresAt = System.currentTimeMillis() + 60_000,
            productId = "securecall_pro_monthly",
            packageName = "com.securecall.app.free",
            catalogVersion = "securecall-play-v1"
        ))
        TierManager.applyTier(context)
        val buildTier = BuildConfig.FLAVOR.uppercase()
        val paidTier = if (buildTier == "PREMIUM") "PREMIUM" else "PRO"
        assertEquals(paidTier, FeatureProviderRegistry.get().tier)

        manager.clearSubscription()
        TierManager.applyTier(context)
        assertEquals(buildTier, FeatureProviderRegistry.get().tier)
    }

    private class MemoryPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") ((values[key] as? Set<String>)?.toMutableSet() ?: defValues)
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val changes = linkedMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { changes[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
                apply { changes[key!!] = values?.toSet() }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { changes[key!!] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { changes[key!!] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { changes[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { changes[key!!] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { changes[key!!] = null }
            override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
            override fun commit(): Boolean {
                if (clearRequested) values.clear()
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}

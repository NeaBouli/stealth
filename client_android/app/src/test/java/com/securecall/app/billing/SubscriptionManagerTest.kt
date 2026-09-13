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
    fun testerProofUsesHardwareBindingAndNeverUnlocksOtherPackages() {
        val keys = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
        val publicKey = encoder.encodeToString(keys.public.encoded.takeLast(32).toByteArray())
        var now = 1700000000L
        var device: String? = "a".repeat(64)
        val text = "v=1\niss=stealthx\naud=securecall-tester\nsub=synthetic-A\npkg=com.securecall.app.premium\ntier=PREMIUM\ngrant=${"b".repeat(64)}\ndevice=$device\niat=$now\nexp=${now + 3600}"
        val payload = "sct1.${encoder.encodeToString(text.toByteArray())}"
        val signer = java.security.Signature.getInstance("Ed25519")
        signer.initSign(keys.private)
        signer.update(payload.toByteArray())
        val proof = "$payload.${encoder.encodeToString(signer.sign())}"
        fun store(flavor: String = "premium", packageName: String = "com.securecall.app.premium",
                  verifier: String = publicKey) = DirectEntitlementStore(prefs, { "synthetic-A" }, "",
            flavor, "unused-for-gifts", { now }, verifier, { device }, packageName)
        assertFalse(store(verifier = "").accept(proof))
        assertFalse(store(flavor = "free").accept(proof))
        assertFalse(store(flavor = "pro").accept(proof))
        assertFalse(store(packageName = "com.securecall.app.free").accept(proof))
        assertTrue(store().accept(proof))
        assertEquals("PREMIUM", store().currentTier())
        val disabled = DirectEntitlementStore(prefs, { "synthetic-A" }, "", "premium", "unused-for-gifts",
            { now }, publicKey, { device }, "com.securecall.app.premium", testerEnabled = false)
        assertFalse(disabled.accept(proof))
        assertEquals("FREE", disabled.currentTier())
        assertTrue(store().accept(proof))
        device = "c".repeat(64)
        assertEquals("FREE", store().currentTier())
        device = null
        assertEquals("FREE", store().currentTier())
        device = "a".repeat(64)
        now += 3600
        assertEquals("FREE", store().currentTier())
        now -= 3600
        assertTrue(store().revoke())
        assertEquals("FREE", store().currentTier())
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

        val buildTier = "FREE"
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
        val buildTier = "FREE"
        val paidTier = if (BuildConfig.FLAVOR == "free") "PRO" else "FREE"
        assertEquals(paidTier, FeatureProviderRegistry.get().tier)

        manager.clearSubscription()
        TierManager.applyTier(context)
        assertEquals(buildTier, FeatureProviderRegistry.get().tier)
    }

    @Test
    fun directProofPersistsRestoresExpiresAndRevokes() {
        val fixture = DirectFixture()
        val store = fixture.store()
        assertEquals("FREE", store.currentTier())
        assertTrue(store.accept(fixture.token))
        assertEquals("PRO", fixture.store().currentTier())
        fixture.now += 3600
        assertEquals("FREE", fixture.store().currentTier())
        assertEquals(fixture.token, store.tokenForRefresh())
        assertTrue(store.revoke())
        assertEquals(null, fixture.store().tokenForRefresh())
        assertEquals("FREE", fixture.store().currentTier())
    }

    @Test
    fun directProofRejectsIdentityDriftAndClockRollback() {
        val fixture = DirectFixture()
        assertTrue(fixture.store().accept(fixture.token))
        fixture.subject = "other-device"
        assertEquals("FREE", fixture.store().currentTier())
        fixture.subject = "test-device"
        fixture.now -= 301
        assertEquals("FREE", fixture.store().currentTier())
    }

    @Test
    fun directProofCannotGrantPlayOrDifferentDirectPlan() {
        val fixture = DirectFixture()
        assertFalse(fixture.store("free").accept(fixture.token))
        assertFalse(fixture.store("premium").accept(fixture.token))
        assertEquals("FREE", fixture.store("premium").currentTier())
    }

    @Test
    fun invalidReplacementPreservesPreviouslyVerifiedProof() {
        val fixture = DirectFixture()
        val store = fixture.store()
        assertTrue(store.accept(fixture.token))
        assertFalse(store.accept("invalid.replacement"))
        assertEquals("PRO", store.currentTier())
        assertEquals(fixture.token, store.tokenForRefresh())
    }

    @Test
    fun repeatedAcceptanceCannotMoveVerificationClockBackward() {
        val fixture = DirectFixture()
        fixture.now += 3000
        val store = fixture.store()
        assertTrue(store.accept(fixture.token))
        val verifiedTime = fixture.now
        fixture.now -= 200
        assertTrue(store.accept(fixture.token))
        assertEquals(verifiedTime, fixture.prefs.getLong("last_verified_time", 0))
        fixture.now -= 200
        assertFalse(store.accept(fixture.token))
        assertEquals("FREE", store.currentTier())
    }

    private class DirectFixture {
        val prefs = MemoryPreferences()
        var now = 1700000000L
        var subject = "test-device"
        val keys = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
        val release = "securecall-android-1.0.50-vc78017-api36"
        val token: String

        init {
            val payload = listOf("v=2", "iss=stealthx", "aud=securecall", "sub=$subject", "tier=PRO",
                "product=vlabs_securecall_pro_lifetime", "iat=$now", "exp=${now + 3600}",
                "order=${"a".repeat(32)}", "catalog=stealthx-lifetime-v1",
                "offer=securecall-pro-eur-1500-lifetime-v1", "release=$release").joinToString("\n")
            val encoded = encoder.encodeToString(payload.toByteArray(Charsets.US_ASCII))
            val signer = java.security.Signature.getInstance("Ed25519")
            signer.initSign(keys.private)
            signer.update(encoded.toByteArray(Charsets.US_ASCII))
            token = "$encoded.${encoder.encodeToString(signer.sign())}"
        }

        fun store(flavor: String = "pro") = DirectEntitlementStore(prefs, { subject },
            encoder.encodeToString(keys.public.encoded.takeLast(32).toByteArray()), flavor, release, { now })
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

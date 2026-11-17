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
    fun authenticatedIdentityMigrationPreservesExistingDirectEntitlement() {
        val fixture = DirectFixture()
        val legacy = fixture.subject
        fixture.subject = "sc-${"a".repeat(43)}"
        val store = DirectEntitlementStore(
            fixture.prefs,
            { fixture.subject },
            fixture.encoder.encodeToString(fixture.keys.public.encoded.takeLast(32).toByteArray()),
            "pro",
            fixture.release,
            { fixture.now },
            legacySubject = { legacy },
        )
        assertTrue(store.accept(fixture.token))
        assertEquals("PRO", store.currentTier())
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
    fun mismatchedResponseCannotConsumeItsPendingReceipt() {
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
        assertEquals(1, manager.pendingReceiptCount())
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

    // ─── Pending receipts isolated from verified state (SC-PLAY-LIFETIME-CONSISTENCY) ───

    private var clockNow = 1_700_000_000_000L
    private val pkg = "com.securecall.app.free"
    private val catalog = "securecall-play-v1"

    private fun clocked() = SubscriptionManager(context) { clockNow }

    private fun ack(
        m: SubscriptionManager,
        requestId: String,
        productId: String,
        tier: SubscriptionTier = SubscriptionTier.PRO,
        expiresAt: Long = clockNow + 60_000,
        success: Boolean = true
    ) = m.onVerificationAck(success, requestId, tier, expiresAt, productId, pkg, catalog)

    private fun verifiedMonthly(m: SubscriptionManager, token: String = "tok-monthly"): String {
        val requestId = m.recordPendingPurchase(token, "securecall_pro_monthly")
        assertEquals(SubscriptionManager.VerificationOutcome.ACCEPTED,
            ack(m, requestId, "securecall_pro_monthly", expiresAt = clockNow + 86_400_000))
        return requestId
    }

    @Test
    fun pendingRestoreKeepsVerifiedRightUntilFiniteExpiry() {
        val m = clocked()
        verifiedMonthly(m)
        m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
        m.recordPendingPurchase("tok-monthly", "securecall_pro_monthly")
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
        assertEquals("tok-monthly", m.getPurchaseToken())
        clockNow += 86_400_001
        assertEquals("finite expiry still applies", SubscriptionTier.FREE, m.getCurrentTier())
    }

    @Test
    fun staleUnrelatedAndCrossRequestAcksDoNotClearState() {
        val m = clocked()
        val consumed = verifiedMonthly(m)
        val pending = m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
        val unrelated = "00000000-0000-0000-0000-000000000000"
        assertEquals(SubscriptionManager.VerificationOutcome.STALE_IGNORED,
            ack(m, unrelated, "securecall_premium_lifetime", success = false))
        assertEquals(SubscriptionManager.VerificationOutcome.STALE_IGNORED,
            ack(m, consumed, "securecall_pro_monthly", success = false))
        assertEquals(SubscriptionManager.VerificationOutcome.STALE_IGNORED,
            ack(m, "", "securecall_premium_lifetime", SubscriptionTier.PREMIUM))
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
        assertEquals(1, m.pendingReceiptCount())

        // A failed answer for the pending request drops only that receipt.
        assertEquals(SubscriptionManager.VerificationOutcome.REJECTED,
            ack(m, pending, "securecall_premium_lifetime", SubscriptionTier.FREE, 0, success = false))
        assertEquals(0, m.pendingReceiptCount())
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
    }

    @Test
    fun reRecordingSameTokenMakesOlderRequestStale() {
        val m = clocked()
        val first = m.recordPendingPurchase("tok-lifetime", "securecall_pro_lifetime")
        val second = m.recordPendingPurchase("tok-lifetime", "securecall_pro_lifetime")
        assertEquals(1, m.pendingReceiptCount())
        assertEquals(SubscriptionManager.VerificationOutcome.STALE_IGNORED, ack(m, first, "securecall_pro_lifetime"))
        assertEquals(SubscriptionTier.FREE, m.getCurrentTier())
        assertEquals(SubscriptionManager.VerificationOutcome.ACCEPTED, ack(m, second, "securecall_pro_lifetime"))
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
    }

    @Test
    fun crossRequestSuccessCannotConsumeAnotherPendingReceipt() {
        val m = clocked()
        verifiedMonthly(m)
        val pro = m.recordPendingPurchase("tok-pro", "securecall_pro_lifetime")
        m.recordPendingPurchase("tok-premium", "securecall_premium_lifetime")
        assertEquals(SubscriptionManager.VerificationOutcome.STALE_IGNORED,
            ack(m, pro, "securecall_premium_lifetime", SubscriptionTier.PREMIUM))
        assertEquals(2, m.pendingReceiptCount())
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
        assertEquals(SubscriptionManager.VerificationOutcome.AWAITING_OTHER_RECEIPTS,
            ack(m, pro, "securecall_pro_lifetime"))
    }

    @Test
    fun concurrentSubsAndInappRestoreIsDeterministicAndNeedsExplicitPolicy() {
        for (subsFirst in listOf(true, false)) {
            prefs.edit().clear().commit()
            val m = clocked()
            val subs = m.recordPendingPurchase("tok-monthly", "securecall_pro_monthly")
            val inapp = m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
            val answers = listOf(
                { ack(m, subs, "securecall_pro_monthly") },
                { ack(m, inapp, "securecall_premium_lifetime", SubscriptionTier.PREMIUM) }
            ).let { if (subsFirst) it else it.reversed() }
            assertEquals(SubscriptionManager.VerificationOutcome.AWAITING_OTHER_RECEIPTS, answers[0]())
            assertEquals(SubscriptionTier.FREE, m.getCurrentTier())
            assertEquals(SubscriptionManager.VerificationOutcome.COEXISTENCE_POLICY_REQUIRED, answers[1]())
            assertEquals("no implicit rank or upgrade choice", SubscriptionTier.FREE, m.getCurrentTier())
            assertEquals("neither record is cancelled", 2, m.pendingReceiptCount())
        }
        // One rejected, one verified: the verified purchase applies in either order.
        for (rejectFirst in listOf(true, false)) {
            prefs.edit().clear().commit()
            val m = clocked()
            val subs = m.recordPendingPurchase("tok-monthly", "securecall_pro_monthly")
            val inapp = m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
            val reject = { ack(m, subs, "securecall_pro_monthly", SubscriptionTier.FREE, 0, success = false) }
            val accept = { ack(m, inapp, "securecall_premium_lifetime", SubscriptionTier.PREMIUM) }
            if (rejectFirst) { reject(); accept() } else { accept(); reject() }
            assertEquals(SubscriptionTier.PREMIUM, m.getCurrentTier())
            assertEquals("tok-lifetime", m.getPurchaseToken())
        }
    }

    @Test
    fun verifiedRightIsKeptWhenAnotherPurchaseNeedsCoexistencePolicy() {
        val m = clocked()
        verifiedMonthly(m)
        val inapp = m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
        assertEquals(SubscriptionManager.VerificationOutcome.COEXISTENCE_POLICY_REQUIRED,
            ack(m, inapp, "securecall_premium_lifetime", SubscriptionTier.PREMIUM))
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
        assertEquals("tok-monthly", m.getPurchaseToken())

        // A failed status request cannot implicitly select a different purchase.
        val snapshot = m.statusSnapshot()!!
        assertFalse(m.applyStatusResponse(snapshot, true, "{\"valid\":false,\"tier\":\"FREE\"}"))
        assertEquals(SubscriptionTier.FREE, m.getCurrentTier())
        assertEquals(1, m.pendingReceiptCount())
    }

    @Test
    fun httpStatusAnswerIsBoundToItsOriginalReceiptSnapshot() {
        val m = clocked()
        verifiedMonthly(m)
        val stale = m.statusSnapshot()!!
        // A newer accepted answer for the same purchase lands while HTTP is in flight.
        val again = m.recordPendingPurchase("tok-monthly", "securecall_pro_monthly")
        assertEquals(SubscriptionManager.VerificationOutcome.ACCEPTED,
            ack(m, again, "securecall_pro_monthly", expiresAt = clockNow + 172_800_000))

        assertFalse("stale failure cannot clear the newer receipt", m.applyStatusResponse(stale, false, null))
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
        val staleValid = "{\"valid\":true,\"tier\":\"PREMIUM\",\"productId\":\"securecall_pro_monthly\"," +
            "\"packageName\":\"$pkg\",\"catalogVersion\":\"$catalog\",\"expiresAt\":${clockNow + 1000}}"
        assertFalse("stale success cannot overwrite the newer receipt", m.applyStatusResponse(stale, true, staleValid))
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
        assertEquals(clockNow + 172_800_000, m.getExpiresAt())

        // A current failure still fails closed for the verified right, but keeps pending receipts.
        m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
        assertFalse(m.applyStatusResponse(m.statusSnapshot()!!, false, null))
        assertEquals(SubscriptionTier.FREE, m.getCurrentTier())
        assertEquals(1, m.pendingReceiptCount())
    }

    @Test
    fun finalizedRestoreAckCannotBeCancelledByADuplicateFailure() {
        val m = clocked()
        val subs = m.recordPendingPurchase("tok-monthly", "securecall_pro_monthly")
        val inapp = m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
        assertEquals(SubscriptionManager.VerificationOutcome.AWAITING_OTHER_RECEIPTS,
            ack(m, subs, "securecall_pro_monthly"))
        assertEquals(SubscriptionManager.VerificationOutcome.STALE_IGNORED,
            ack(m, subs, "securecall_pro_monthly", success = false))
        assertEquals(2, m.pendingReceiptCount())
        assertEquals(SubscriptionManager.VerificationOutcome.COEXISTENCE_POLICY_REQUIRED,
            ack(m, inapp, "securecall_premium_lifetime", SubscriptionTier.PREMIUM))
        assertEquals(SubscriptionTier.FREE, m.getCurrentTier())
        assertEquals(2, SubscriptionManager(context) { clockNow }.pendingReceiptCount())
    }

    @Test
    fun unansweredReceiptIsNotSilentlyAbandonedToSelectAnotherPurchase() {
        val m = clocked()
        val inapp = m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
        m.recordPendingPurchase("tok-monthly", "securecall_pro_monthly")
        clockNow += 16 * 60_000
        assertEquals(SubscriptionManager.VerificationOutcome.AWAITING_OTHER_RECEIPTS,
            ack(m, inapp, "securecall_premium_lifetime", SubscriptionTier.PREMIUM))
        assertEquals(SubscriptionTier.FREE, m.getCurrentTier())
        assertEquals(2, m.pendingReceiptCount())
    }

    @Test
    fun failedPreferenceCommitsCannotGrantInMemoryAndCanRetry() {
        val m = clocked()
        verifiedMonthly(m)
        prefs.failCommitAt = prefs.commits + 1
        assertTrue(runCatching { m.recordPendingPurchase("tok-new", "securecall_pro_lifetime") }.isFailure)
        assertEquals(0, m.pendingReceiptCount())
        assertEquals(SubscriptionTier.PRO, SubscriptionManager(context) { clockNow }.getCurrentTier())

        val request = m.recordPendingPurchase("tok-monthly", "securecall_pro_monthly")
        val oldExpiry = m.getExpiresAt()
        prefs.failCommitAt = prefs.commits + 1
        assertEquals(SubscriptionManager.VerificationOutcome.PERSISTENCE_FAILED,
            ack(m, request, "securecall_pro_monthly"))
        assertEquals(oldExpiry, m.getExpiresAt())
        assertEquals(1, m.pendingReceiptCount())

        // Receipt write succeeds but the final verified-state write fails.
        prefs.failCommitAt = prefs.commits + 2
        assertEquals(SubscriptionManager.VerificationOutcome.PERSISTENCE_FAILED,
            ack(m, request, "securecall_pro_monthly"))
        assertEquals(oldExpiry, m.getExpiresAt())
        assertEquals(SubscriptionManager.VerificationOutcome.ACCEPTED,
            ack(m, request, "securecall_pro_monthly"))
        assertEquals(0, m.pendingReceiptCount())
    }

    @Test
    fun freshGrantWriteFailureLeavesNoPaidStateEvenForAnotherManager() {
        val m = clocked()
        val request = m.recordPendingPurchase("tok-lifetime", "securecall_pro_lifetime")
        prefs.failCommitAt = prefs.commits + 2
        assertEquals(SubscriptionManager.VerificationOutcome.PERSISTENCE_FAILED,
            ack(m, request, "securecall_pro_lifetime"))
        assertEquals(SubscriptionTier.FREE, SubscriptionManager(context) { clockNow }.getCurrentTier())
        assertEquals(SubscriptionManager.VerificationOutcome.ACCEPTED,
            ack(m, request, "securecall_pro_lifetime"))
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
    }

    @Test
    fun httpSnapshotCannotMatchAgainAfterExplicitResetAndSameTokenRestore() {
        val m = clocked()
        verifiedMonthly(m)
        val stale = m.statusSnapshot()!!
        m.clearSubscription()
        verifiedMonthly(m)
        assertFalse(m.applyStatusResponse(stale, false, null))
        assertEquals(SubscriptionTier.PRO, m.getCurrentTier())
    }

    @Test
    fun currentStatusCannotResurrectQueuedReceiptForTheSameRevokedPurchase() {
        val m = clocked()
        verifiedMonthly(m)
        val same = m.recordPendingPurchase("tok-monthly", "securecall_pro_monthly")
        m.recordPendingPurchase("tok-lifetime", "securecall_premium_lifetime")
        ack(m, same, "securecall_pro_monthly")
        assertFalse(m.applyStatusResponse(m.statusSnapshot()!!, true, "{\"valid\":false}"))
        assertEquals(1, m.pendingReceiptCount())
        assertEquals(SubscriptionTier.FREE, m.getCurrentTier())
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
        assertTrue(fixture.prefs.contains("token"))
        assertTrue(fixture.prefs.contains("last_verified_time"))
        assertEquals("PRO", fixture.store().currentTier())
        fixture.now += 3600
        assertEquals("FREE", fixture.store().currentTier())
        assertEquals(fixture.token, store.tokenForRefresh())
        assertTrue(store.revoke())
        assertFalse(fixture.prefs.contains("token"))
        assertFalse(fixture.prefs.contains("last_verified_time"))
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
        var commits = 0
        var failCommitAt = -1

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
                commits += 1
                if (clearRequested) values.clear()
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                // Match Android: memory changes even when the disk write returns false.
                return commits != failCommitAt
            }
            override fun apply() {
                commit()
            }
        }
    }
}

package com.securecall.app.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.securecall.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BillingManagerTest {
    private val activity: Activity = mock()
    private val listener: BillingManager.BillingListener = mock()
    private val manager = BillingManager(activity, listener)
    private val result: BillingResult = mock<BillingResult>().also {
        whenever(it.responseCode).thenReturn(BillingClient.BillingResponseCode.OK)
    }

    private fun purchase(products: List<String>, token: String, state: Int = Purchase.PurchaseState.PURCHASED): Purchase =
        mock<Purchase>().also {
            whenever(it.products).thenReturn(products)
            whenever(it.purchaseToken).thenReturn(token)
            whenever(it.purchaseState).thenReturn(state)
        }

    @Test
    fun restoreCallbacksBindEachTokenToItsOwnProductWithoutMutableSideChannel() {
        manager.onPurchasesUpdated(result, listOf(
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "synthetic-pro-token"),
            purchase(listOf(BuildConfig.SKU_PREMIUM_LIFETIME), "synthetic-premium-token")
        ))

        inOrder(listener) {
            verify(listener).onPurchaseCompleted(SubscriptionTier.PRO, "synthetic-pro-token", BuildConfig.SKU_PRO_LIFETIME)
            verify(listener).onPurchaseCompleted(SubscriptionTier.PREMIUM, "synthetic-premium-token", BuildConfig.SKU_PREMIUM_LIFETIME)
        }
    }

    @Test
    fun pendingUnknownAndAmbiguousPurchasesCannotStartVerification() {
        manager.onPurchasesUpdated(result, listOf(
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "synthetic-pending", Purchase.PurchaseState.PENDING),
            purchase(listOf("unknown_premium"), "synthetic-unknown"),
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME, BuildConfig.SKU_PREMIUM_LIFETIME), "synthetic-multi"),
            purchase(emptyList(), "synthetic-empty")
        ))

        verify(listener, never()).onPurchaseCompleted(any(), any(), any())
    }

    @Test
    fun offeredCatalogIsExactlyTheApprovedInAppLifetimeAndActivationProducts() {
        assertEquals("securecall_pro_lifetime", BuildConfig.SKU_PRO_LIFETIME)
        assertEquals("securecall_premium_lifetime", BuildConfig.SKU_PREMIUM_LIFETIME)
        @Suppress("UNCHECKED_CAST")
        val offered = BillingManager::class.java.getDeclaredField("lifetimeSkus")
            .apply { isAccessible = true }.get(manager) as List<String>
        assertEquals(listOf("securecall_pro_lifetime", "securecall_premium_lifetime",
            "securecall_premium_activation_code"), offered)
        val recurring = Regex("(?i)month|year|subscri|SUBS")
        assertTrue(offered.none { recurring.containsMatchIn(it) })
        assertTrue(BuildConfig::class.java.declaredFields.none { recurring.containsMatchIn(it.name) })
        // No SUBS product query, SUBS offer-token launch or recurring catalog exists.
        val members = BillingManager::class.java.declaredMethods.map { it.name } +
            BillingManager::class.java.declaredFields.map { it.name }
        assertTrue(members.none { recurring.containsMatchIn(it) })
        assertEquals(listOf("launchInAppPurchaseFlow"),
            BillingManager::class.java.declaredMethods.map { it.name }.filter { it.startsWith("launch") })
    }

    @Test
    fun productQueryIssuesOnlyTheSingleInAppLifetimeRequest() {
        val client: BillingClient = mock()
        BillingManager::class.java.getDeclaredField("billingClient").apply {
            isAccessible = true
            set(manager, client)
        }
        BillingManager::class.java.getDeclaredMethod("queryInAppProducts").apply {
            isAccessible = true
            invoke(manager)
        }
        verify(client, times(1)).queryProductDetailsAsync(any<QueryProductDetailsParams>(), any())
    }

    @Test
    fun recurringReceiptsAreRefusedInPurchaseAndRestore() {
        val historical = listOf("pro_monthly", "premium_monthly", "securecall_pro_monthly",
            "securecall_pro_yearly", "securecall_premium_monthly", "securecall_premium_yearly")
        manager.onPurchasesUpdated(result, historical.map { purchase(listOf(it), "synthetic-$it") })
        verify(listener, never()).onPurchaseCompleted(any(), any(), any())

        val callbacks = queryRestore(manager)
        callbacks.getValue(BillingClient.ProductType.SUBS).onQueryPurchasesResponse(result,
            historical.map { purchase(listOf(it), "synthetic-$it") })
        callbacks.getValue(BillingClient.ProductType.INAPP).onQueryPurchasesResponse(result,
            listOf(purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "synthetic-lifetime")))
        verify(listener).onPurchasesRestored(listOf(
            BillingManager.RestoredPurchase(SubscriptionTier.PRO, "synthetic-lifetime", BuildConfig.SKU_PRO_LIFETIME)))
    }

    @Test
    fun subsAndInappQueriesDeliverOneImmutableRestoreBatchInEitherOrder() {
        for (inappFirst in listOf(true, false)) {
            val observer: BillingManager.BillingListener = mock()
            val subject = BillingManager(activity, observer)
            val callbacks = queryRestore(subject)
            val lifetimeProducts = mutableListOf(BuildConfig.SKU_PRO_LIFETIME)
            val subs = listOf(purchase(listOf("securecall_pro_monthly"), "synthetic-monthly"))
            val inapp = listOf(purchase(lifetimeProducts, "synthetic-pro-lifetime"),
                purchase(listOf(BuildConfig.SKU_PREMIUM_LIFETIME), "synthetic-lifetime"))
            val first = if (inappFirst) BillingClient.ProductType.INAPP else BillingClient.ProductType.SUBS
            val second = if (inappFirst) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
            callbacks.getValue(first).onQueryPurchasesResponse(result, if (inappFirst) inapp else subs)
            verify(observer, never()).onPurchasesRestored(any())
            if (inappFirst) lifetimeProducts[0] = "changed-after-callback"
            callbacks.getValue(second).onQueryPurchasesResponse(result, if (inappFirst) subs else inapp)
            verify(observer).onPurchasesRestored(listOf(
                BillingManager.RestoredPurchase(SubscriptionTier.PRO, "synthetic-pro-lifetime", BuildConfig.SKU_PRO_LIFETIME),
                BillingManager.RestoredPurchase(SubscriptionTier.PREMIUM, "synthetic-lifetime", BuildConfig.SKU_PREMIUM_LIFETIME)
            ))
            verify(observer, never()).onPurchaseCompleted(any(), any(), any())
        }
    }

    @Test
    fun failedRestoreHalfAndDestroyedManagerCannotDeliverASelectedSubset() {
        val callbacks = queryRestore(manager)
        val failure: BillingResult = mock()
        whenever(failure.responseCode).thenReturn(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        callbacks.getValue(BillingClient.ProductType.SUBS).onQueryPurchasesResponse(failure, emptyList())
        callbacks.getValue(BillingClient.ProductType.INAPP).onQueryPurchasesResponse(result,
            listOf(purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "synthetic-inapp")))
        verify(listener).onPurchaseFailed(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            "Unable to restore all purchases")
        verify(listener, never()).onPurchasesRestored(any())
        verify(listener, never()).onPurchaseCompleted(any(), any(), any())

        val stale = queryRestore(manager)
        manager.destroy()
        stale.values.forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        verify(listener, never()).onPurchasesRestored(any())
    }

    @Test
    fun emptySuccessfulFreshInventoryLaunchesOnlyAfterBothQueriesAndOnlyOnce() {
        val (client, callbacks) = purchaseClient()
        guardedQuery()
        verify(client, never()).launchBillingFlow(any(), any())
        callbacks[0].onQueryPurchasesResponse(result, emptyList())
        verify(client, never()).launchBillingFlow(any(), any())
        callbacks[1].onQueryPurchasesResponse(result, emptyList())
        callbacks[1].onQueryPurchasesResponse(result, emptyList())
        verify(client, times(1)).launchBillingFlow(any(), any())
        verify(listener, never()).onPurchaseCompleted(any(), any(), any())
    }

    @Test
    fun eitherOwnedLifetimeBlocksSameItemAndOtherLifetimeBeforeCharge() {
        for (owned in listOf(BuildConfig.SKU_PRO_LIFETIME, BuildConfig.SKU_PREMIUM_LIFETIME)) {
            for (target in listOf(BuildConfig.SKU_PRO_LIFETIME, BuildConfig.SKU_PREMIUM_LIFETIME)) {
                val observer: BillingManager.BillingListener = mock()
                val subject = BillingManager(activity, observer)
                val (client, callbacks) = purchaseClient(subject)
                guardedQuery(subject, target)
                callbacks[0].onQueryPurchasesResponse(result, emptyList())
                callbacks[1].onQueryPurchasesResponse(result, listOf(purchase(listOf(owned), "synthetic-owned")))
                verify(client, never()).launchBillingFlow(any(), any())
                verify(observer).onPurchaseFailed(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
                    "Restore existing purchases; another lifetime purchase is unavailable")
                verify(observer, never()).onPurchaseCompleted(any(), any(), any())
            }
        }
    }

    @Test
    fun pendingUnknownAmbiguousAndBlankTokenInventoryCannotBecomeEmptyEligibility() {
        val uncertain = listOf(
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "synthetic-pending", Purchase.PurchaseState.PENDING),
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "synthetic-unspecified", Purchase.PurchaseState.UNSPECIFIED_STATE),
            purchase(listOf("unknown-product"), "synthetic-unknown"),
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME, BuildConfig.SKU_PREMIUM_LIFETIME), "synthetic-multiple"),
            purchase(emptyList(), "synthetic-empty-products"),
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "")
        )
        for (receipt in uncertain) {
            val observer: BillingManager.BillingListener = mock()
            val subject = BillingManager(activity, observer)
            val (client, callbacks) = purchaseClient(subject)
            guardedQuery(subject)
            callbacks[0].onQueryPurchasesResponse(result, emptyList())
            callbacks[1].onQueryPurchasesResponse(result, listOf(receipt))
            verify(client, never()).launchBillingFlow(any(), any())
            verify(observer).onPurchaseFailed(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                "Purchase ownership is unresolved")
        }
    }

    @Test
    fun failedQueryIsNotSuccessfulEmptyInventoryInEitherOrder() {
        val failure: BillingResult = mock()
        whenever(failure.responseCode).thenReturn(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        for (failedHalf in listOf(0, 1)) {
            val subject = BillingManager(activity, mock())
            val (client, callbacks) = purchaseClient(subject)
            guardedQuery(subject)
            callbacks[failedHalf].onQueryPurchasesResponse(failure, emptyList())
            verify(client, never()).launchBillingFlow(any(), any())
            callbacks[1 - failedHalf].onQueryPurchasesResponse(result, emptyList())
            verify(client, never()).launchBillingFlow(any(), any())
        }
    }

    @Test
    fun multipleOwnedReceiptsCannotSelectAnUpgradeBeforeCharge() {
        val (client, callbacks) = purchaseClient()
        guardedQuery()
        callbacks[0].onQueryPurchasesResponse(result, emptyList())
        callbacks[1].onQueryPurchasesResponse(result, listOf(
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "synthetic-pro"),
            purchase(listOf(BuildConfig.SKU_PREMIUM_LIFETIME), "synthetic-premium")))
        verify(client, never()).launchBillingFlow(any(), any())
        verify(listener, never()).onPurchaseCompleted(any(), any(), any())
    }

    @Test
    fun completionOfEarlierRestoreInvalidatesAnOverlappingEmptyPurchaseQuery() {
        val (client, callbacks) = purchaseClient()
        BillingManager::class.java.getDeclaredMethod("queryExistingPurchases", ProductDetails::class.java).apply {
            isAccessible = true
            invoke(manager, null)
        }
        guardedQuery()
        callbacks.take(2).forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        callbacks.drop(2).forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        verify(listener).onPurchasesRestored(emptyList())
        verify(client, never()).launchBillingFlow(any(), any())
    }

    @Test
    fun unsuccessfulLaunchReleasesReservationButNeverReusesEmptyInventory() {
        val (client, callbacks) = purchaseClient()
        val failure: BillingResult = mock()
        whenever(failure.responseCode).thenReturn(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        whenever(failure.debugMessage).thenReturn("synthetic launch failure")
        whenever(client.launchBillingFlow(any<Activity>(), any<BillingFlowParams>())).thenReturn(failure)
        guardedQuery()
        callbacks.toList().forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        verify(listener).onPurchaseFailed(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            "synthetic launch failure")
        guardedQuery()
        assertEquals(4, callbacks.size)
        verify(client, times(1)).launchBillingFlow(any(), any())
    }

    @Test
    fun duplicateTapsStayReservedThroughBillingResultThenNeedNewInventory() {
        val (client, callbacks) = purchaseClient()
        guardedQuery()
        guardedQuery()
        assertEquals(2, callbacks.size)
        callbacks.toList().forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        guardedQuery()
        assertEquals(2, callbacks.size)
        verify(client, times(1)).launchBillingFlow(any(), any())

        val canceled: BillingResult = mock()
        whenever(canceled.responseCode).thenReturn(BillingClient.BillingResponseCode.USER_CANCELED)
        manager.onPurchasesUpdated(canceled, null)
        guardedQuery()
        assertEquals(4, callbacks.size)
        verify(client, times(1)).launchBillingFlow(any(), any())
        callbacks.drop(2).forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        verify(client, times(2)).launchBillingFlow(any(), any())
    }

    @Test
    fun purchaseUpdateInvalidatesPendingQueryAndCannotBeErasedByLaterEmptyInventory() {
        val (client, callbacks) = purchaseClient()
        guardedQuery()
        manager.onPurchasesUpdated(result, listOf(
            purchase(listOf(BuildConfig.SKU_PRO_LIFETIME), "synthetic-new-owned")))
        callbacks.toList().forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        guardedQuery()
        callbacks.drop(2).forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        verify(client, never()).launchBillingFlow(any(), any())
        verify(listener).onPurchaseCompleted(SubscriptionTier.PRO, "synthetic-new-owned", BuildConfig.SKU_PRO_LIFETIME)
    }

    @Test
    fun destroyedManagerAndLostReadinessCannotLaunchFromLateQuery() {
        for (destroy in listOf(true, false)) {
            val subject = BillingManager(activity, mock())
            val (client, callbacks) = purchaseClient(subject)
            guardedQuery(subject)
            if (destroy) subject.destroy() else whenever(client.isReady).thenReturn(false)
            callbacks.forEach { it.onQueryPurchasesResponse(result, emptyList()) }
            verify(client, never()).launchBillingFlow(any(), any())
        }
    }

    @Test
    fun recordedVerifiedRightBlocksEvenEmptyProviderInventoryWithoutMutatingEntitlement() {
        val (client, callbacks) = purchaseClient()
        val prefs = activity.getSharedPreferences("securecall_subscription", Context.MODE_PRIVATE)
        whenever(prefs.getString("purchase_token", "")).thenReturn("synthetic-verified-pro")
        whenever(prefs.getString("tier", SubscriptionTier.FREE.name)).thenReturn(SubscriptionTier.PRO.name)
        whenever(prefs.getBoolean("server_verified", false)).thenReturn(true)
        whenever(prefs.getString("package_name", null)).thenReturn("com.securecall.app.free")
        whenever(prefs.getString("catalog_version", null)).thenReturn(BuildConfig.PLAY_CATALOG_VERSION)
        whenever(prefs.getString("product_id", "")).thenReturn(BuildConfig.SKU_PRO_LIFETIME)
        whenever(prefs.getLong("expires_at", 0L)).thenReturn(Long.MAX_VALUE)
        val verifiedState = SubscriptionManager(activity)
        assertEquals(SubscriptionTier.PRO, verifiedState.getCurrentTier())

        guardedQuery()
        callbacks.forEach { it.onQueryPurchasesResponse(result, emptyList()) }

        verify(client, never()).launchBillingFlow(any(), any())
        verify(prefs, never()).edit()
        assertEquals(SubscriptionTier.PRO, verifiedState.getCurrentTier())
        assertEquals("synthetic-verified-pro", verifiedState.getPurchaseToken())
    }

    @Test
    fun recordedPendingReceiptBlocksOtherwiseEmptyInventory() {
        val (client, callbacks) = purchaseClient()
        val prefs = activity.getSharedPreferences("securecall_subscription", Context.MODE_PRIVATE)
        val id = "00000000-0000-0000-0000-000000000001"
        whenever(prefs.getStringSet("pending_receipt_ids", emptySet())).thenReturn(setOf(id))
        for ((field, value) in mapOf("token" to "synthetic-pending", "product" to BuildConfig.SKU_PRO_LIFETIME,
                "package" to "com.securecall.app.free", "catalog" to BuildConfig.PLAY_CATALOG_VERSION)) {
            whenever(prefs.getString("pending_receipt.$id.$field", null)).thenReturn(value)
        }
        assertEquals(1, SubscriptionManager(activity).pendingReceiptCount())
        guardedQuery()
        callbacks.forEach { it.onQueryPurchasesResponse(result, emptyList()) }
        verify(client, never()).launchBillingFlow(any(), any())
        verify(prefs, never()).edit()
    }

    @Test
    fun blockedSameItemPurchaseDoesNotDisableExistingRestoreHop() {
        val (client, callbacks) = purchaseClient()
        val owned = purchase(listOf(BuildConfig.SKU_PREMIUM_LIFETIME), "synthetic-restore")
        guardedQuery()
        callbacks.toList().forEach { it.onQueryPurchasesResponse(result, listOf(owned)) }
        verify(client, never()).launchBillingFlow(any(), any())
        val restore = queryRestore(manager)
        restore.getValue(BillingClient.ProductType.SUBS).onQueryPurchasesResponse(result, emptyList())
        restore.getValue(BillingClient.ProductType.INAPP).onQueryPurchasesResponse(result, listOf(owned))
        verify(listener).onPurchasesRestored(listOf(BillingManager.RestoredPurchase(
            SubscriptionTier.PREMIUM, "synthetic-restore", BuildConfig.SKU_PREMIUM_LIFETIME)))
    }

    @Test
    fun billing82BuilderRequiresPackageMetadataEvenForAnInAppProduct() {
        val details: ProductDetails = mock()
        whenever(details.productId).thenReturn(BuildConfig.SKU_PREMIUM_LIFETIME)
        whenever(details.productType).thenReturn(BillingClient.ProductType.INAPP)
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details).build()

        // Billing8.2.1 calls ProductDetails.zza().isEmpty(); an unstubbed mock returns null.
        assertThrows(NullPointerException::class.java) {
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        }
        whenever(details.zza()).thenReturn("com.securecall.app.free")
        assertNotNull(BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
    }

    private fun purchaseClient(subject: BillingManager = manager): Pair<BillingClient, MutableList<PurchasesResponseListener>> {
        val client: BillingClient = mock()
        val callbacks = mutableListOf<PurchasesResponseListener>()
        val prefs: SharedPreferences = mock()
        whenever(activity.packageName).thenReturn("com.securecall.app.free")
        whenever(activity.getSharedPreferences("securecall_subscription", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getString("purchase_token", "")).thenReturn("")
        whenever(client.isReady).thenReturn(true)
        whenever(client.launchBillingFlow(any<Activity>(), any<BillingFlowParams>())).thenReturn(result)
        doAnswer { callbacks += it.arguments[1] as PurchasesResponseListener; null }
            .whenever(client).queryPurchasesAsync(any<QueryPurchasesParams>(), any())
        BillingManager::class.java.getDeclaredField("billingClient").apply {
            isAccessible = true
            set(subject, client)
        }
        return client to callbacks
    }

    private fun guardedQuery(subject: BillingManager = manager, sku: String = BuildConfig.SKU_PREMIUM_LIFETIME) {
        val details: ProductDetails = mock()
        whenever(details.productId).thenReturn(sku)
        whenever(details.productType).thenReturn(BillingClient.ProductType.INAPP)
        // SDK8.2.1's public obfuscated accessor reads packageName from real ProductDetails JSON.
        whenever(details.zza()).thenReturn("com.securecall.app.free")
        // Exercise the real query->launch hop without enabling the closed BuildConfig sales flag.
        BillingManager::class.java.getDeclaredMethod("queryExistingPurchases", ProductDetails::class.java).apply {
            isAccessible = true
            invoke(subject, details)
        }
    }

    private fun queryRestore(subject: BillingManager): Map<String, PurchasesResponseListener> {
        val client: BillingClient = mock()
        val callbacks = mutableMapOf<String, PurchasesResponseListener>()
        doAnswer {
            // Billing 8.2.1 exposes no public type getter; queries are issued SUBS then INAPP.
            val type = if (callbacks.isEmpty()) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
            callbacks[type] = it.arguments[1] as PurchasesResponseListener
            null
        }.whenever(client).queryPurchasesAsync(any<QueryPurchasesParams>(), any())
        BillingManager::class.java.getDeclaredField("billingClient").apply {
            isAccessible = true
            set(subject, client)
        }
        BillingManager::class.java.getDeclaredMethod("queryExistingPurchases", ProductDetails::class.java).apply {
            isAccessible = true
            invoke(subject, null)
        }
        assertEquals(2, callbacks.size)
        return callbacks
    }
}

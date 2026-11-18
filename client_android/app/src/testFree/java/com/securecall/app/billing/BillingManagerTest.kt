package com.securecall.app.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.securecall.app.BuildConfig
import org.junit.Assert.assertEquals
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
        BillingManager::class.java.getDeclaredMethod("queryExistingPurchases").apply {
            isAccessible = true
            invoke(subject)
        }
        assertEquals(2, callbacks.size)
        return callbacks
    }
}

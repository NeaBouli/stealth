package com.securecall.app.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryPurchasesParams
import com.securecall.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
            purchase(listOf(BuildConfig.SKU_PREMIUM_MONTHLY), "synthetic-premium-token")
        ))

        inOrder(listener) {
            verify(listener).onPurchaseCompleted(SubscriptionTier.PRO, "synthetic-pro-token", BuildConfig.SKU_PRO_LIFETIME)
            verify(listener).onPurchaseCompleted(SubscriptionTier.PREMIUM, "synthetic-premium-token", BuildConfig.SKU_PREMIUM_MONTHLY)
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
    fun subsAndInappQueriesDeliverOneImmutableRestoreBatchInEitherOrder() {
        for (inappFirst in listOf(true, false)) {
            val observer: BillingManager.BillingListener = mock()
            val subject = BillingManager(activity, observer)
            val callbacks = queryRestore(subject)
            val monthlyProducts = mutableListOf(BuildConfig.SKU_PRO_MONTHLY)
            val monthly = listOf(purchase(monthlyProducts, "synthetic-monthly"))
            val inapp = listOf(purchase(listOf(BuildConfig.SKU_PREMIUM_LIFETIME), "synthetic-lifetime"))
            val first = if (inappFirst) BillingClient.ProductType.INAPP else BillingClient.ProductType.SUBS
            val second = if (inappFirst) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
            callbacks.getValue(first).onQueryPurchasesResponse(result, if (inappFirst) inapp else monthly)
            verify(observer, never()).onPurchasesRestored(any())
            if (!inappFirst) monthlyProducts[0] = "changed-after-callback"
            callbacks.getValue(second).onQueryPurchasesResponse(result, if (inappFirst) monthly else inapp)
            verify(observer).onPurchasesRestored(listOf(
                BillingManager.RestoredPurchase(SubscriptionTier.PRO, "synthetic-monthly", BuildConfig.SKU_PRO_MONTHLY),
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

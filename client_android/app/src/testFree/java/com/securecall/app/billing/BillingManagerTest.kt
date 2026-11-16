package com.securecall.app.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.securecall.app.BuildConfig
import org.junit.Test
import org.mockito.kotlin.any
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
}

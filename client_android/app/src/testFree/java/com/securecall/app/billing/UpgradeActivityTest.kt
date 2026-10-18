package com.securecall.app.billing

import android.widget.TextView
import com.android.billingclient.api.ProductDetails
import com.securecall.app.BuildConfig
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UpgradeActivityTest {
    private val activity = Mockito.mock(UpgradeActivity::class.java, Answers.CALLS_REAL_METHODS)
    private val manager: BillingManager = mock()
    private val status: TextView = mock()

    @Before
    fun setUp() {
        for ((name, value) in mapOf("billingManager" to manager, "tvStatus" to status)) {
            UpgradeActivity::class.java.getDeclaredField(name).apply {
                isAccessible = true
                set(activity, value)
            }
        }
    }

    @Test
    fun activationCodeAndLifetimeOffersUseInAppPurchaseFlow() {
        for (sku in listOf(
            BuildConfig.SKU_PREMIUM_ACTIVATION_CODE,
            BuildConfig.SKU_PRO_LIFETIME,
            BuildConfig.SKU_PREMIUM_LIFETIME
        )) {
            val details: ProductDetails = mock()
            val offer: ProductDetails.OneTimePurchaseOfferDetails = mock()
            whenever(manager.getProductDetails(sku)).thenReturn(details)
            whenever(details.oneTimePurchaseOfferDetails).thenReturn(offer)

            launchPurchase(sku)

            verify(manager).launchInAppPurchaseFlow(details)
        }
        verify(manager, never()).launchPurchaseFlow(any(), any())
    }

    @Test
    fun subscriptionOfferUsesItsOfferToken() {
        val details: ProductDetails = mock()
        val offer: ProductDetails.SubscriptionOfferDetails = mock()
        whenever(manager.getProductDetails(BuildConfig.SKU_PREMIUM_MONTHLY)).thenReturn(details)
        whenever(details.subscriptionOfferDetails).thenReturn(listOf(offer))
        whenever(offer.offerToken).thenReturn("subscription-offer-token")

        launchPurchase(BuildConfig.SKU_PREMIUM_MONTHLY)

        verify(manager).launchPurchaseFlow(details, "subscription-offer-token")
        verify(manager, never()).launchInAppPurchaseFlow(any())
    }

    @Test
    fun missingOfferDoesNotLaunchPurchase() {
        val details: ProductDetails = mock()
        whenever(manager.getProductDetails(BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)).thenReturn(details)

        launchPurchase(BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)

        verify(status).text = "No offer available"
        verify(manager, never()).launchInAppPurchaseFlow(any())
        verify(manager, never()).launchPurchaseFlow(any(), any())
    }

    private fun launchPurchase(sku: String) {
        UpgradeActivity::class.java.getDeclaredMethod("launchPurchase", String::class.java).apply {
            isAccessible = true
            invoke(activity, sku)
        }
    }
}

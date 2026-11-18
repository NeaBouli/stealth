package com.securecall.app.billing

import android.content.Intent
import android.content.SharedPreferences
import android.widget.TextView
import android.widget.Button
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.securecall.app.BuildConfig
import com.securecall.app.R
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UpgradeActivityTest {
    private val activity = Mockito.mock(UpgradeActivity::class.java, Answers.CALLS_REAL_METHODS)
    private val manager: BillingManager = mock()
    private val status: TextView = mock()

    @Before
    fun setUp() {
        doReturn("Launching purchase...").whenever(activity).getString(R.string.upgrade_launching_purchase)
        doReturn("No offer available").whenever(activity).getString(R.string.upgrade_no_offer_available)
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
            whenever(details.productId).thenReturn(sku)
            whenever(details.productType).thenReturn(BillingClient.ProductType.INAPP)
            whenever(details.oneTimePurchaseOfferDetails).thenReturn(offer)
            whenever(offer.formattedPrice).thenReturn("synthetic Play price")

            launchPurchase(sku)

            verify(manager).launchInAppPurchaseFlow(details)
        }
    }

    @Test
    fun subscriptionOfferCannotReplaceALifetimeOffer() {
        val details: ProductDetails = mock()
        val offer: ProductDetails.SubscriptionOfferDetails = mock()
        whenever(manager.getProductDetails(BuildConfig.SKU_PREMIUM_LIFETIME)).thenReturn(details)
        whenever(details.productId).thenReturn(BuildConfig.SKU_PREMIUM_LIFETIME)
        whenever(details.productType).thenReturn(BillingClient.ProductType.SUBS)
        whenever(details.subscriptionOfferDetails).thenReturn(listOf(offer))
        whenever(offer.offerToken).thenReturn("subscription-offer-token")

        launchPurchase(BuildConfig.SKU_PREMIUM_LIFETIME)

        verify(manager, never()).launchInAppPurchaseFlow(any())
    }

    @Test
    fun missingOfferDoesNotLaunchPurchase() {
        val details: ProductDetails = mock()
        whenever(manager.getProductDetails(BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)).thenReturn(details)
        whenever(details.productId).thenReturn(BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)
        whenever(details.productType).thenReturn(BillingClient.ProductType.INAPP)
        val expectedStatus = activity.getString(R.string.upgrade_no_offer_available)

        launchPurchase(BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)

        verify(status).text = expectedStatus
        verify(manager, never()).launchInAppPurchaseFlow(any())
    }

    @Test
    fun mismatchedProductAndBlankPriceDoNotLaunchPurchase() {
        val sku = BuildConfig.SKU_PRO_LIFETIME
        val details: ProductDetails = mock()
        val offer: ProductDetails.OneTimePurchaseOfferDetails = mock()
        whenever(manager.getProductDetails(sku)).thenReturn(details)
        whenever(details.productType).thenReturn(BillingClient.ProductType.INAPP)
        whenever(details.oneTimePurchaseOfferDetails).thenReturn(offer)
        whenever(offer.formattedPrice).thenReturn("synthetic Play price")
        whenever(details.productId).thenReturn(BuildConfig.SKU_PREMIUM_LIFETIME)
        launchPurchase(sku)

        whenever(details.productId).thenReturn(sku)
        whenever(offer.formattedPrice).thenReturn(" ")
        launchPurchase(sku)

        verify(manager, never()).launchInAppPurchaseFlow(any())
    }

    @Test
    fun lifetimeButtonStaysDisabledForSubscriptionDetails() {
        val button: Button = mock()
        val details: ProductDetails = mock()
        whenever(details.productId).thenReturn(BuildConfig.SKU_PRO_LIFETIME)
        whenever(details.productType).thenReturn(BillingClient.ProductType.SUBS)
        doReturn(button).whenever(activity).findViewById<Button>(R.id.btnProLifetime)

        UpgradeActivity::class.java.getDeclaredMethod("bindPurchaseButton", List::class.java,
            Int::class.javaPrimitiveType, String::class.java, String::class.java).apply {
            isAccessible = true
            invoke(activity, listOf(details), R.id.btnProLifetime, BuildConfig.SKU_PRO_LIFETIME, "Pro Lifetime")
        }

        verify(button).isEnabled = false
        verify(button).text = "Pro Lifetime"
    }

    @Test
    fun lifetimePurchaseUsesInPackageVerificationNotTheCodeScreen() {
        val subscriptions = routeCompletionsThroughUiThread()
        whenever(subscriptions.recordPendingPurchase(any(), any()))
            .thenThrow(IllegalStateException("synthetic pending persistence failure"))

        for (sku in listOf(BuildConfig.SKU_PRO_LIFETIME, BuildConfig.SKU_PREMIUM_LIFETIME)) {
            activity.onPurchaseCompleted(SubscriptionTier.fromProductId(sku), "synthetic-token", sku)
            verify(subscriptions).recordPendingPurchase(eq("synthetic-token"), eq(sku))
        }
        verify(activity, never()).startActivity(anyOrNull<Intent>())
    }

    @Test
    fun explicitActivationCodePurchaseKeepsTheCodeScreen() {
        val subscriptions = routeCompletionsThroughUiThread()

        activity.onPurchaseCompleted(SubscriptionTier.PREMIUM, "synthetic-token",
            BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)

        verify(activity).startActivity(anyOrNull<Intent>())
        verify(subscriptions, never()).recordPendingPurchase(any(), any())
    }

    @Test
    fun restoreRegistersEveryReceiptBeforeAttemptingVerification() {
        val subscriptions = routeCompletionsThroughUiThread()
        whenever(subscriptions.recordPendingPurchase("synthetic-pro-lifetime", BuildConfig.SKU_PRO_LIFETIME))
            .thenReturn("request-pro-lifetime")
        whenever(subscriptions.recordPendingPurchase("synthetic-lifetime", BuildConfig.SKU_PREMIUM_LIFETIME))
            .thenReturn("request-lifetime")
        whenever(subscriptions.getCurrentTier()).thenReturn(SubscriptionTier.FREE)
        UpgradeActivity::class.java.getDeclaredField("tvCurrentTier").apply {
            isAccessible = true
            set(activity, mock<TextView>())
        }

        activity.onPurchasesRestored(listOf(
            BillingManager.RestoredPurchase(SubscriptionTier.PRO, "synthetic-pro-lifetime", BuildConfig.SKU_PRO_LIFETIME),
            BillingManager.RestoredPurchase(SubscriptionTier.PREMIUM, "synthetic-lifetime", BuildConfig.SKU_PREMIUM_LIFETIME)
        ))

        // No service is connected: each send takes the discard path, after both records.
        inOrder(subscriptions) {
            verify(subscriptions).recordPendingPurchase("synthetic-pro-lifetime", BuildConfig.SKU_PRO_LIFETIME)
            verify(subscriptions).recordPendingPurchase("synthetic-lifetime", BuildConfig.SKU_PREMIUM_LIFETIME)
            verify(subscriptions).discardPendingPurchase("request-pro-lifetime")
            verify(subscriptions).discardPendingPurchase("request-lifetime")
        }
        verify(activity, never()).startActivity(anyOrNull<Intent>())
    }

    private fun routeCompletionsThroughUiThread(): SubscriptionManager {
        val subscriptions: SubscriptionManager = mock()
        val prefs: SharedPreferences = mock()
        UpgradeActivity::class.java.getDeclaredField("subscriptionManager").apply {
            isAccessible = true
            set(activity, subscriptions)
        }
        doAnswer { (it.arguments[0] as Runnable).run(); null }.whenever(activity).runOnUiThread(any())
        doNothing().whenever(activity).startActivity(anyOrNull<Intent>())
        doReturn(prefs).whenever(activity).getSharedPreferences(any(), any())
        doReturn(activity).whenever(activity).applicationContext
        doReturn("pending").whenever(activity).getString(R.string.upgrade_verification_pending)
        doReturn("unable").whenever(activity).getString(R.string.upgrade_unable_to_verify_purchase)
        return subscriptions
    }

    private fun launchPurchase(sku: String) {
        UpgradeActivity::class.java.getDeclaredMethod("launchPurchase", String::class.java).apply {
            isAccessible = true
            invoke(activity, sku)
        }
    }
}

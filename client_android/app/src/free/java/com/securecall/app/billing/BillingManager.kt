package com.securecall.app.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import com.securecall.app.BuildConfig

/**
 * Google Play Billing Library wrapper (FREE flavor only).
 *
 * Manages subscription purchases for upgrading from FREE to PRO/PREMIUM.
 */
class BillingManager(
    private val activity: Activity,
    private val listener: BillingListener
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
    }

    interface BillingListener {
        fun onProductsLoaded(products: List<ProductDetails>)
        fun onPurchaseCompleted(tier: SubscriptionTier, token: String)
        fun onPurchaseFailed(errorCode: Int, message: String)
        fun onBillingDisconnected()
    }

    private var billingClient: BillingClient? = null
    private var productDetailsList: List<ProductDetails> = emptyList()

    private val allSkus = listOf(
        BuildConfig.SKU_PRO_MONTHLY,
        BuildConfig.SKU_PRO_YEARLY,
        BuildConfig.SKU_PREMIUM_MONTHLY,
        BuildConfig.SKU_PREMIUM_YEARLY
    )

    fun init() {
        billingClient = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    queryProducts()
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                    listener.onPurchaseFailed(result.responseCode, result.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                listener.onBillingDisconnected()
            }
        })
    }

    private fun queryProducts() {
        val productList = allSkus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList = detailsList
                Log.d(TAG, "Products loaded: ${detailsList.size}")
                listener.onProductsLoaded(detailsList)
            } else {
                Log.e(TAG, "queryProductDetails failed: ${result.debugMessage}")
            }
        }
    }

    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handlePurchase(purchase)
                    }
                }
            }
        }
    }

    fun launchPurchaseFlow(productDetails: ProductDetails, offerToken: String) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    fun getProductDetails(sku: String): ProductDetails? {
        return productDetailsList.firstOrNull { it.productId == sku }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
                listener.onPurchaseFailed(result.responseCode, "Purchase canceled")
            }
            else -> {
                Log.e(TAG, "Purchase error: ${result.responseCode} - ${result.debugMessage}")
                listener.onPurchaseFailed(result.responseCode, result.debugMessage)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        val tier = SubscriptionTier.fromProductId(productId)
        val token = purchase.purchaseToken

        Log.d(TAG, "Purchase completed: productId=$productId, tier=$tier")

        // Acknowledge the purchase
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token)
                .build()
            billingClient?.acknowledgePurchase(ackParams) { ackResult ->
                if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged")
                } else {
                    Log.e(TAG, "Acknowledge failed: ${ackResult.debugMessage}")
                }
            }
        }

        listener.onPurchaseCompleted(tier, token)
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}

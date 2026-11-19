package com.securecall.app.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import com.securecall.app.BuildConfig

/**
 * Google Play Billing Library wrapper (FREE flavor only).
 *
 * Sells only one-time (INAPP) Pro/Premium lifetime products and the activation-code
 * product. No recurring (SUBS) product is queried, offered or accepted.
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
        fun onPurchaseCompleted(tier: SubscriptionTier, token: String, productId: String)
        fun onPurchasesRestored(purchases: List<RestoredPurchase>)
        fun onPurchaseFailed(errorCode: Int, message: String)
        fun onBillingDisconnected()
    }

    data class RestoredPurchase(val tier: SubscriptionTier, val token: String, val productId: String)

    private var billingClient: BillingClient? = null
    private var productDetailsList: List<ProductDetails> = emptyList()
    private val purchaseLock = Any()
    private var purchaseInFlight: Any? = null
    private var inventoryVersion = 0L
    private var ownershipObserved = false

    private val lifetimeSkus = listOf(
        BuildConfig.SKU_PRO_LIFETIME,
        BuildConfig.SKU_PREMIUM_LIFETIME,
        BuildConfig.SKU_PREMIUM_ACTIVATION_CODE
    )

    fun init() {
        if (!BuildConfig.BILLING_ENABLED) {
            listener.onPurchaseFailed(
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                "Purchases are currently unavailable"
            )
            return
        }
        billingClient = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    queryInAppProducts()
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                    listener.onPurchaseFailed(result.responseCode, result.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                synchronized(purchaseLock) {
                    inventoryVersion++
                    purchaseInFlight = null
                }
                Log.w(TAG, "Billing service disconnected")
                listener.onBillingDisconnected()
            }
        })
    }

    private fun queryInAppProducts() {
        val productList = lifetimeSkus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { result, queryResult ->
            val detailsList = queryResult.productDetailsList
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList = productDetailsList + detailsList
                Log.d(TAG, "In-app products loaded: ${detailsList.size}")
                listener.onProductsLoaded(productDetailsList)
            } else {
                Log.e(TAG, "queryInAppProducts failed: ${result.debugMessage}")
            }
        }
    }

    private fun queryExistingPurchases(productDetails: ProductDetails? = null) {
        val client = billingClient ?: return
        val attempt = synchronized(purchaseLock) {
            if (productDetails == null) null else {
                if (!client.isReady || purchaseInFlight != null) {
                    listener.onPurchaseFailed(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                        "Purchase ownership check unavailable or already in progress")
                    return
                }
                Any().also { purchaseInFlight = it }
            }
        }
        val version = synchronized(purchaseLock) { inventoryVersion }
        val results = mutableMapOf<String, List<RestoredPurchase>>()
        var failed: BillingResult? = null
        var unresolvedInventory = false
        var ownedInventory = false
        var delivered = false
        fun completed(type: String, result: BillingResult, purchases: List<Purchase>) {
            synchronized(results) {
                if (delivered || billingClient !== client || results.containsKey(type)) return
                if (result.responseCode != BillingClient.BillingResponseCode.OK) failed = result
                // Do not filter pending/unknown receipts into an apparently empty inventory.
                unresolvedInventory = unresolvedInventory || purchases.any {
                    it.purchaseState != Purchase.PurchaseState.PURCHASED
                        || it.products.singleOrNull() !in lifetimeSkus
                        || it.purchaseToken.isBlank()
                }
                ownedInventory = ownedInventory || purchases.isNotEmpty()
                if (purchases.isNotEmpty()) synchronized(purchaseLock) { ownershipObserved = true }
                results[type] = purchases.mapNotNull { purchaseReceipt(it) }
                if (results.size != 2) return
                delivered = true
                val error = failed
                if (attempt != null && productDetails != null) {
                    synchronized(purchaseLock) {
                        if (billingClient !== client || purchaseInFlight !== attempt) return
                        val failure = when {
                            error != null -> error.responseCode to "Unable to restore all purchases"
                            inventoryVersion != version || !client.isReady || unresolvedInventory ->
                                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE to "Purchase ownership is unresolved"
                            ownedInventory || ownershipObserved -> BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED to
                                "Restore existing purchases; another lifetime purchase is unavailable"
                            else -> try {
                                val verifiedState = SubscriptionManager(activity)
                                if (verifiedState.getPurchaseToken().isNotBlank()
                                    || verifiedState.pendingReceiptCount() != 0) {
                                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED to
                                        "Restore existing purchases; another lifetime purchase is unavailable"
                                } else null
                            } catch (_: RuntimeException) {
                                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE to "Purchase ownership is unresolved"
                            }
                        }
                        if (failure != null) {
                            purchaseInFlight = null
                            listener.onPurchaseFailed(failure.first, failure.second)
                            return
                        }
                        try {
                            val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails).build()
                            )).build()
                            val launchResult = client.launchBillingFlow(activity, params)
                            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                                if (purchaseInFlight === attempt) purchaseInFlight = null
                                listener.onPurchaseFailed(launchResult.responseCode, launchResult.debugMessage)
                            }
                        } catch (_: RuntimeException) {
                            if (purchaseInFlight === attempt) purchaseInFlight = null
                            listener.onPurchaseFailed(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                                "Unable to launch purchase")
                        }
                    }
                    return
                }
                synchronized(purchaseLock) { inventoryVersion++ }
                if (error != null) {
                    // A missing half is not evidence that only the other purchase exists.
                    listener.onPurchaseFailed(error.responseCode, "Unable to restore all purchases")
                } else {
                    listener.onPurchasesRestored((results.getValue(BillingClient.ProductType.SUBS)
                        + results.getValue(BillingClient.ProductType.INAPP)).distinct())
                }
            }
        }
        // Neutral restore plumbing: no recurring product is accepted, so any SUBS
        // receipt is dropped by purchaseReceipt and only INAPP receipts can restore.
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(subsParams) { result, purchases ->
            completed(BillingClient.ProductType.SUBS, result, purchases)
        }

        // Check in-app purchases (lifetime)
        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(inappParams) { result, purchases ->
            completed(BillingClient.ProductType.INAPP, result, purchases)
        }
    }

    fun launchInAppPurchaseFlow(productDetails: ProductDetails) {
        if (!BuildConfig.BILLING_ENABLED) return
        if (productDetails.productId in listOf(BuildConfig.SKU_PRO_LIFETIME, BuildConfig.SKU_PREMIUM_LIFETIME)) {
            queryExistingPurchases(productDetails)
            return
        }
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
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
        synchronized(purchaseLock) {
            inventoryVersion++
            purchaseInFlight = null
            if (!purchases.isNullOrEmpty()) ownershipObserved = true
        }
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
        val receipt = purchaseReceipt(purchase) ?: return
        // The backend acknowledges only after verification and durable persistence.
        listener.onPurchaseCompleted(receipt.tier, receipt.token, receipt.productId)
    }

    private fun purchaseReceipt(purchase: Purchase): RestoredPurchase? {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return null
        val productId = purchase.products.singleOrNull()
            ?.takeIf { it in lifetimeSkus } ?: return null
        val tier = SubscriptionTier.fromProductId(productId)
        val token = purchase.purchaseToken

        Log.d(TAG, "Purchase completed: productId=$productId, tier=$tier")

        return RestoredPurchase(tier, token, productId)
    }

    fun destroy() {
        val client = synchronized(purchaseLock) {
            inventoryVersion++
            purchaseInFlight = null
            billingClient.also { billingClient = null }
        }
        client?.endConnection()
    }
}

package com.securecall.app.billing

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.ProductDetails
import com.securecall.app.BuildConfig
import com.securecall.app.R
import com.securecall.app.net.WebSocketService
import com.securecall.app.ui.EdgeToEdgeHelper

class UpgradeActivity : AppCompatActivity(), BillingManager.BillingListener {

    companion object {
        private const val TAG = "UpgradeActivity"
    }

    private lateinit var billingManager: BillingManager
    private lateinit var subscriptionManager: SubscriptionManager

    private lateinit var tvCurrentTier: TextView
    private lateinit var tvStatus: TextView

    private lateinit var btnProLifetime: Button
    private lateinit var btnPremiumLifetime: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this)
        setContentView(R.layout.activity_upgrade)
        EdgeToEdgeHelper.applySystemBarPaddingToContent(this)

        subscriptionManager = SubscriptionManager(this)

        tvCurrentTier = findViewById(R.id.tvCurrentTier)
        tvStatus = findViewById(R.id.tvStatus)

        btnProLifetime = findViewById(R.id.btnProLifetime)
        btnPremiumLifetime = findViewById(R.id.btnPremiumLifetime)

        updateCurrentTierDisplay()

        // Purchases remain closed until product and finance readiness approve the same offer version.
        val isPlayStore = com.securecall.app.update.UpdateManager.getUpdateUrl(this).contains("market://")
        val billingAvailable = BuildConfig.BILLING_ENABLED && isPlayStore
        if (!billingAvailable) {
            Log.d(TAG, "Billing gate closed — hiding the complete purchase surface")
            findViewById<View>(R.id.billingPurchaseContent).visibility = View.GONE
            tvStatus.text = "Purchases are currently unavailable"
        }

        billingManager = BillingManager(this, this)
        if (billingAvailable) billingManager.init()

        // Lifetime buttons
        btnProLifetime.setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            launchPurchase(BuildConfig.SKU_PRO_LIFETIME)
        }
        btnPremiumLifetime.setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            launchPurchase(BuildConfig.SKU_PREMIUM_LIFETIME)
        }

        // Activation Code purchase button (Play Store only)
        findViewById<Button>(R.id.btnActivationCode).setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            launchPurchase(BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)
        }

        // Subscription buttons (Play Store only)
        findViewById<Button>(R.id.btnProMonthly).setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            launchPurchase(BuildConfig.SKU_PRO_MONTHLY)
        }
        findViewById<Button>(R.id.btnProYearly).setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            launchPurchase(BuildConfig.SKU_PRO_YEARLY)
        }
        findViewById<Button>(R.id.btnPremiumMonthly).setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            launchPurchase(BuildConfig.SKU_PREMIUM_MONTHLY)
        }
        findViewById<Button>(R.id.btnPremiumYearly).setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            launchPurchase(BuildConfig.SKU_PREMIUM_YEARLY)
        }

        // Restore (Play Store only)
        findViewById<Button>(R.id.btnRestore).setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            tvStatus.text = "Restoring purchases..."
            billingManager.destroy()
            billingManager = BillingManager(this, this)
            billingManager.init()
        }
    }

    private fun launchPurchase(sku: String) {
        val details = billingManager.getProductDetails(sku)
        if (details == null) {
            tvStatus.text = "Product not available"
            Log.w(TAG, "Product not found: $sku")
            return
        }

        // Lifetime products are INAPP, subscriptions are SUBS
        if (sku.contains("lifetime")) {
            val offerDetails = details.oneTimePurchaseOfferDetails
            if (offerDetails == null) {
                tvStatus.text = "No offer available"
                return
            }
            tvStatus.text = "Launching purchase..."
            billingManager.launchInAppPurchaseFlow(details)
        } else {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                tvStatus.text = "No offer available"
                return
            }
            tvStatus.text = "Launching purchase..."
            billingManager.launchPurchaseFlow(details, offerToken)
        }
    }

    private fun updateCurrentTierDisplay() {
        val tier = subscriptionManager.getCurrentTier()
        tvCurrentTier.text = "Current Plan: ${tier.displayName}"
    }

    // ===================== BillingListener =====================

    override fun onProductsLoaded(products: List<ProductDetails>) {
        runOnUiThread {
            bindProductPrice(products, R.id.btnProMonthly, BuildConfig.SKU_PRO_MONTHLY, "Upgrade to Pro")
            bindProductPrice(products, R.id.btnProYearly, BuildConfig.SKU_PRO_YEARLY, "Pro yearly")
            bindProductPrice(products, R.id.btnPremiumMonthly, BuildConfig.SKU_PREMIUM_MONTHLY, "Upgrade to Premium")
            bindProductPrice(products, R.id.btnPremiumYearly, BuildConfig.SKU_PREMIUM_YEARLY, "Premium yearly")
            bindProductPrice(products, R.id.btnProLifetime, BuildConfig.SKU_PRO_LIFETIME, "Pro Lifetime")
            bindProductPrice(products, R.id.btnPremiumLifetime, BuildConfig.SKU_PREMIUM_LIFETIME, "Premium Lifetime")
            bindProductPrice(products, R.id.btnActivationCode, BuildConfig.SKU_PREMIUM_ACTIVATION_CODE, "Activation Code")
            tvStatus.text = "${products.size} products loaded"
            Log.d(TAG, "Products loaded: ${products.map { it.productId }}")
        }
    }

    private fun bindProductPrice(
        products: List<ProductDetails>,
        buttonId: Int,
        productId: String,
        label: String
    ) {
        val button = findViewById<Button>(buttonId)
        val details = products.firstOrNull { it.productId == productId }
        val formattedPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice
            ?: details?.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice

        button.isEnabled = formattedPrice != null
        button.text = if (formattedPrice == null) label else "$label — $formattedPrice"
    }

    override fun onPurchaseCompleted(tier: SubscriptionTier, token: String) {
        runOnUiThread {
            // Check if this is an activation code purchase
            val lastProduct = billingManager.getLastPurchasedProductId()
            if (lastProduct in setOf(
                    BuildConfig.SKU_PRO_LIFETIME,
                    BuildConfig.SKU_PREMIUM_LIFETIME,
                    BuildConfig.SKU_PREMIUM_ACTIVATION_CODE
                )
            ) {
                // Launch PurchaseResultActivity to show generated code
                val intent = Intent(this, PurchaseResultActivity::class.java).apply {
                    putExtra(PurchaseResultActivity.EXTRA_PURCHASE_TOKEN, token)
                    putExtra(PurchaseResultActivity.EXTRA_PRODUCT_ID, lastProduct)
                    putExtra(PurchaseResultActivity.EXTRA_PACKAGE_NAME, packageName)
                }
                startActivity(intent)
                tvStatus.text = "Purchase pending server verification"
                return@runOnUiThread
            }

            val productId = lastProduct.orEmpty()
            val requestId = runCatching {
                subscriptionManager.recordPendingPurchase(token, productId)
            }.getOrElse {
                subscriptionManager.clearSubscription()
                com.securecall.app.config.TierManager.applyTier(this)
                tvStatus.text = "Unable to verify purchase"
                return@runOnUiThread
            }
            com.securecall.app.config.TierManager.applyTier(this)
            sendVerificationToBackend(token, productId, requestId)
            updateCurrentTierDisplay()
            tvStatus.text = tier.displayName + " purchase pending server verification"
            Toast.makeText(this, "Verifying purchase securely...", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPurchaseFailed(errorCode: Int, message: String) {
        runOnUiThread {
            tvStatus.text = "Purchase failed: $message"
            Log.e(TAG, "Purchase failed: code=$errorCode, msg=$message")
        }
    }

    override fun onBillingDisconnected() {
        runOnUiThread {
            tvStatus.text = "Billing disconnected — tap Restore to retry"
        }
    }

    private fun sendVerificationToBackend(
        purchaseToken: String,
        productId: String,
        requestId: String
    ) {
        val ws = WebSocketService.instance
        if (ws == null || !ws.isConnected) {
            subscriptionManager.clearSubscription()
            com.securecall.app.config.TierManager.applyTier(this)
            tvStatus.text = "Verification unavailable"
            return
        }
        val json = org.json.JSONObject().apply {
            put("type", "SUBSCRIPTION_VERIFY")
            put("requestId", requestId)
            put("purchaseToken", purchaseToken)
            put("productId", productId)
            put("packageName", packageName)
            put("catalogVersion", BuildConfig.PLAY_CATALOG_VERSION)
        }.toString()
        ws.sendMessage(json)
        Log.d(TAG, "SUBSCRIPTION_VERIFY sent to backend")
    }

    override fun onDestroy() {
        billingManager.destroy()
        super.onDestroy()
    }
}

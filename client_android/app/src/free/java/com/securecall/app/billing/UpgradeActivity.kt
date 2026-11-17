package com.securecall.app.billing

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.BillingClient
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
            tvStatus.text = getString(R.string.upgrade_purchases_unavailable)
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

        // Restore (Play Store only)
        findViewById<Button>(R.id.btnRestore).setOnClickListener {
            if (!billingAvailable) return@setOnClickListener
            tvStatus.text = getString(R.string.upgrade_restoring_purchases)
            billingManager.destroy()
            billingManager = BillingManager(this, this)
            billingManager.init()
        }
    }

    private fun launchPurchase(sku: String) {
        val details = billingManager.getProductDetails(sku)
        if (details == null) {
            tvStatus.text = getString(R.string.upgrade_product_unavailable)
            Log.w(TAG, "Product not found: $sku")
            return
        }

        // These controls sell one-time products only. Historical subscription
        // offers must never be substituted for a missing lifetime product.
        if (sku !in setOf(BuildConfig.SKU_PRO_LIFETIME, BuildConfig.SKU_PREMIUM_LIFETIME,
                BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)
            || details.productId != sku
            || details.productType != BillingClient.ProductType.INAPP
            || details.oneTimePurchaseOfferDetails?.formattedPrice.isNullOrBlank()) {
            tvStatus.text = getString(R.string.upgrade_no_offer_available)
            return
        }
        tvStatus.text = getString(R.string.upgrade_launching_purchase)
        billingManager.launchInAppPurchaseFlow(details)
    }

    private fun updateCurrentTierDisplay() {
        val tier = subscriptionManager.getCurrentTier()
        tvCurrentTier.text = getString(R.string.upgrade_current_edition, tier.displayName)
    }

    // ===================== BillingListener =====================

    override fun onPurchasesRestored(purchases: List<BillingManager.RestoredPurchase>) {
        runOnUiThread {
            val pending = mutableListOf<Pair<BillingManager.RestoredPurchase, String>>()
            try {
                // Register the whole restore before any request can receive an answer.
                for (purchase in purchases.filter { it.productId != BuildConfig.SKU_PREMIUM_ACTIVATION_CODE }) {
                    pending += purchase to subscriptionManager.recordPendingPurchase(purchase.token, purchase.productId)
                }
            } catch (_: IllegalStateException) {
                pending.forEach { subscriptionManager.discardPendingPurchase(it.second) }
                tvStatus.text = getString(R.string.upgrade_unable_to_verify_purchase)
                return@runOnUiThread
            } catch (_: IllegalArgumentException) {
                pending.forEach { subscriptionManager.discardPendingPurchase(it.second) }
                tvStatus.text = getString(R.string.upgrade_unable_to_verify_purchase)
                return@runOnUiThread
            }
            pending.forEach { (purchase, requestId) ->
                sendVerificationToBackend(purchase.token, purchase.productId, requestId)
            }
            purchases.filter { it.productId == BuildConfig.SKU_PREMIUM_ACTIVATION_CODE }.forEach {
                onPurchaseCompleted(it.tier, it.token, it.productId)
            }
            updateCurrentTierDisplay()
        }
    }

    override fun onProductsLoaded(products: List<ProductDetails>) {
        runOnUiThread {
            bindPurchaseButton(products, R.id.btnProLifetime, BuildConfig.SKU_PRO_LIFETIME, getString(R.string.upgrade_pro_lifetime))
            bindPurchaseButton(products, R.id.btnPremiumLifetime, BuildConfig.SKU_PREMIUM_LIFETIME, getString(R.string.upgrade_premium_lifetime))
            bindPurchaseButton(products, R.id.btnActivationCode, BuildConfig.SKU_PREMIUM_ACTIVATION_CODE, getString(R.string.upgrade_activation_code))
            tvStatus.text = getString(R.string.upgrade_products_loaded, products.size)
            Log.d(TAG, "Products loaded: ${products.map { it.productId }}")
        }
    }

    private fun bindPurchaseButton(
        products: List<ProductDetails>,
        buttonId: Int,
        productId: String,
        label: String
    ) {
        val button = findViewById<Button>(buttonId)
        val details = products.firstOrNull {
            it.productId == productId && it.productType == BillingClient.ProductType.INAPP
        }
        val formattedPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice?.takeIf { it.isNotBlank() }

        button.isEnabled = formattedPrice != null
        button.text = if (formattedPrice == null) label else getString(R.string.upgrade_purchase_with_price, label, formattedPrice)
    }

    override fun onPurchaseCompleted(tier: SubscriptionTier, token: String, productId: String) {
        runOnUiThread {
            // Only the explicit activation-code product shows a code. Pro/Premium
            // lifetime purchases unlock this package through server verification below.
            if (productId == BuildConfig.SKU_PREMIUM_ACTIVATION_CODE) {
                // Launch PurchaseResultActivity to show generated code
                val intent = Intent(this, PurchaseResultActivity::class.java).apply {
                    putExtra(PurchaseResultActivity.EXTRA_PURCHASE_TOKEN, token)
                    putExtra(PurchaseResultActivity.EXTRA_PRODUCT_ID, productId)
                    putExtra(PurchaseResultActivity.EXTRA_PACKAGE_NAME, packageName)
                }
                startActivity(intent)
                tvStatus.text = getString(R.string.upgrade_verification_pending)
                return@runOnUiThread
            }

            val requestId = runCatching {
                subscriptionManager.recordPendingPurchase(token, productId)
            }.getOrElse {
                // A receipt that could not be stored grants nothing and leaves verified state intact.
                com.securecall.app.config.TierManager.applyTier(this)
                tvStatus.text = getString(R.string.upgrade_unable_to_verify_purchase)
                return@runOnUiThread
            }
            com.securecall.app.config.TierManager.applyTier(this)
            sendVerificationToBackend(token, productId, requestId)
            updateCurrentTierDisplay()
            tvStatus.text = getString(R.string.upgrade_edition_verification_pending, tier.displayName)
            Toast.makeText(this, getString(R.string.upgrade_verifying_purchase), Toast.LENGTH_LONG).show()
        }
    }

    override fun onPurchaseFailed(errorCode: Int, message: String) {
        runOnUiThread {
            tvStatus.text = getString(R.string.upgrade_purchase_failed, message)
            Log.e(TAG, "Purchase failed: code=$errorCode, msg=$message")
        }
    }

    override fun onBillingDisconnected() {
        runOnUiThread {
            tvStatus.text = getString(R.string.upgrade_billing_disconnected)
        }
    }

    private fun sendVerificationToBackend(
        purchaseToken: String,
        productId: String,
        requestId: String
    ) {
        val ws = WebSocketService.instance
        if (ws == null || !ws.isConnected) {
            subscriptionManager.discardPendingPurchase(requestId)
            com.securecall.app.config.TierManager.applyTier(this)
            tvStatus.text = getString(R.string.upgrade_verification_unavailable)
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

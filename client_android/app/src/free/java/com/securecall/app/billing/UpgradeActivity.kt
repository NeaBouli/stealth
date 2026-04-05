package com.securecall.app.billing

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.ProductDetails
import com.securecall.app.BuildConfig
import com.securecall.app.R
import com.securecall.app.config.FeatureProviderRegistry
import com.securecall.app.config.RuntimeFeatureProvider
import com.securecall.app.net.WebSocketService

class UpgradeActivity : AppCompatActivity(), BillingManager.BillingListener {

    companion object {
        private const val TAG = "UpgradeActivity"
    }

    private lateinit var billingManager: BillingManager
    private lateinit var subscriptionManager: SubscriptionManager

    private lateinit var tvCurrentTier: TextView
    private lateinit var tvStatus: TextView

    // Lifetime offer views
    private lateinit var tvProLicensesLeft: TextView
    private lateinit var tvPremiumLicensesLeft: TextView
    private lateinit var tvProNextPrice: TextView
    private lateinit var tvPremiumNextPrice: TextView
    private lateinit var progressProSold: ProgressBar
    private lateinit var progressPremiumSold: ProgressBar
    private lateinit var btnProLifetime: Button
    private lateinit var btnPremiumLifetime: Button

    // Simulated sold counts (in production, fetch from backend)
    private var proSold = 0
    private var premiumSold = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upgrade)

        subscriptionManager = SubscriptionManager(this)

        tvCurrentTier = findViewById(R.id.tvCurrentTier)
        tvStatus = findViewById(R.id.tvStatus)

        // Lifetime offer views
        tvProLicensesLeft = findViewById(R.id.tvProLicensesLeft)
        tvPremiumLicensesLeft = findViewById(R.id.tvPremiumLicensesLeft)
        tvProNextPrice = findViewById(R.id.tvProNextPrice)
        tvPremiumNextPrice = findViewById(R.id.tvPremiumNextPrice)
        progressProSold = findViewById(R.id.progressProSold)
        progressPremiumSold = findViewById(R.id.progressPremiumSold)
        btnProLifetime = findViewById(R.id.btnProLifetime)
        btnPremiumLifetime = findViewById(R.id.btnPremiumLifetime)

        updateCurrentTierDisplay()
        updateLifetimePricing()

        // Sideload/ADB installs: hide IAP buttons, show code/IFR only
        val isPlayStore = com.securecall.app.update.UpdateManager.getUpdateUrl(this).contains("market://")
        if (!isPlayStore) {
            Log.d(TAG, "Non-Play-Store install — hiding IAP subscription buttons")
            findViewById<Button>(R.id.btnProMonthly).visibility = android.view.View.GONE
            findViewById<Button>(R.id.btnPremiumMonthly).visibility = android.view.View.GONE
            findViewById<Button>(R.id.btnActivationCode).visibility = android.view.View.GONE
            findViewById<Button>(R.id.btnRestore).visibility = android.view.View.GONE
            // Show sideload hint
            tvStatus.text = "Sideload install — upgrade via Activation Code in Settings or IFR Token"
        }

        billingManager = BillingManager(this, this)
        if (isPlayStore) billingManager.init()

        // Lifetime buttons
        btnProLifetime.setOnClickListener {
            if (!isPlayStore) { showSideloadHint(); return@setOnClickListener }
            launchPurchase(BuildConfig.SKU_PRO_LIFETIME)
        }
        btnPremiumLifetime.setOnClickListener {
            if (!isPlayStore) { showSideloadHint(); return@setOnClickListener }
            launchPurchase(BuildConfig.SKU_PREMIUM_LIFETIME)
        }

        // Activation Code purchase button (Play Store only)
        findViewById<Button>(R.id.btnActivationCode).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PREMIUM_ACTIVATION_CODE)
        }

        // Subscription buttons (Play Store only)
        findViewById<Button>(R.id.btnProMonthly).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PRO_MONTHLY)
        }
        findViewById<Button>(R.id.btnProYearly).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PRO_YEARLY)
        }
        findViewById<Button>(R.id.btnPremiumMonthly).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PREMIUM_MONTHLY)
        }
        findViewById<Button>(R.id.btnPremiumYearly).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PREMIUM_YEARLY)
        }

        // Restore (Play Store only)
        findViewById<Button>(R.id.btnRestore).setOnClickListener {
            tvStatus.text = "Restoring purchases..."
            billingManager.destroy()
            billingManager = BillingManager(this, this)
            billingManager.init()
        }
    }

    private fun updateLifetimePricing() {
        // PRO
        val proRemaining = PricingCalculator.getRemainingLicenses("PRO", proSold)
        val proPrice = PricingCalculator.calculateProPrice(proSold)
        val proNextPrice = PricingCalculator.calculateProPrice(proSold + 1)
        tvProLicensesLeft.text = "Only $proRemaining PRO licenses left!"
        btnProLifetime.text = "Buy PRO Lifetime — ${PricingCalculator.formatPrice(proPrice)}"
        tvProNextPrice.text = "Next buyer pays: ${PricingCalculator.formatPrice(proNextPrice)}"
        progressProSold.progress = proSold

        // PREMIUM
        val premiumRemaining = PricingCalculator.getRemainingLicenses("PREMIUM", premiumSold)
        val premiumPrice = PricingCalculator.calculatePremiumPrice(premiumSold)
        val premiumNextPrice = PricingCalculator.calculatePremiumPrice(premiumSold + 1)
        tvPremiumLicensesLeft.text = "Only $premiumRemaining PREMIUM licenses left!"
        btnPremiumLifetime.text = "Buy PREMIUM Lifetime — ${PricingCalculator.formatPrice(premiumPrice)}"
        tvPremiumNextPrice.text = "Next buyer pays: ${PricingCalculator.formatPrice(premiumNextPrice)}"
        progressPremiumSold.progress = premiumSold
    }

    private fun showSideloadHint() {
        Toast.makeText(this, "Go to Settings → Activation Code to upgrade", Toast.LENGTH_LONG).show()
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
            tvStatus.text = "${products.size} products loaded"
            Log.d(TAG, "Products loaded: ${products.map { it.productId }}")
        }
    }

    override fun onPurchaseCompleted(tier: SubscriptionTier, token: String) {
        runOnUiThread {
            // Check if this is an activation code purchase
            val lastProduct = billingManager.getLastPurchasedProductId()
            if (lastProduct == BuildConfig.SKU_PREMIUM_ACTIVATION_CODE) {
                // Launch PurchaseResultActivity to show generated code
                val intent = Intent(this, PurchaseResultActivity::class.java).apply {
                    putExtra(PurchaseResultActivity.EXTRA_PURCHASE_TOKEN, token)
                    putExtra(PurchaseResultActivity.EXTRA_PRODUCT_ID, lastProduct)
                    putExtra(PurchaseResultActivity.EXTRA_PACKAGE_NAME, packageName)
                }
                startActivity(intent)
                tvStatus.text = "Activation code purchased!"
                return@runOnUiThread
            }

            // Normal upgrade flow
            subscriptionManager.updateSubscription(
                tier = tier,
                purchaseToken = token,
                expiresAt = 0L,
                productId = lastProduct ?: ""
            )

            FeatureProviderRegistry.set(RuntimeFeatureProvider(this))

            sendVerificationToBackend(token)

            updateCurrentTierDisplay()
            tvStatus.text = "Upgraded to ${tier.displayName}!"
            Toast.makeText(this, "Upgraded to ${tier.displayName}!", Toast.LENGTH_LONG).show()
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

    private fun sendVerificationToBackend(purchaseToken: String) {
        val ws = WebSocketService.instance ?: return
        val productId = subscriptionManager.getProductId()
        val json = """
            {
              "type": "SUBSCRIPTION_VERIFY",
              "purchaseToken": "$purchaseToken",
              "productId": "$productId"
            }
        """.trimIndent()
        ws.sendMessage(json)
        Log.d(TAG, "SUBSCRIPTION_VERIFY sent to backend")
    }

    override fun onDestroy() {
        billingManager.destroy()
        super.onDestroy()
    }
}

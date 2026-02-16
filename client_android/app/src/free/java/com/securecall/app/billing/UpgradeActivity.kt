package com.securecall.app.billing

import android.os.Bundle
import android.util.Log
import android.widget.Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upgrade)

        subscriptionManager = SubscriptionManager(this)

        tvCurrentTier = findViewById(R.id.tvCurrentTier)
        tvStatus = findViewById(R.id.tvStatus)

        updateCurrentTierDisplay()

        billingManager = BillingManager(this, this)
        billingManager.init()

        // PRO buttons
        findViewById<Button>(R.id.btnProMonthly).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PRO_MONTHLY)
        }
        findViewById<Button>(R.id.btnProYearly).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PRO_YEARLY)
        }

        // PREMIUM buttons
        findViewById<Button>(R.id.btnPremiumMonthly).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PREMIUM_MONTHLY)
        }
        findViewById<Button>(R.id.btnPremiumYearly).setOnClickListener {
            launchPurchase(BuildConfig.SKU_PREMIUM_YEARLY)
        }

        // Restore
        findViewById<Button>(R.id.btnRestore).setOnClickListener {
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

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            tvStatus.text = "No offer available"
            return
        }

        tvStatus.text = "Launching purchase..."
        billingManager.launchPurchaseFlow(details, offerToken)
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
            // Update local subscription state
            subscriptionManager.updateSubscription(
                tier = tier,
                purchaseToken = token,
                expiresAt = 0L, // Will be set by server verification
                productId = "" // Simplified
            )

            // Refresh FeatureProvider
            FeatureProviderRegistry.set(RuntimeFeatureProvider(this))

            // Send verification to backend
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

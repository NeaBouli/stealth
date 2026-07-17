package com.securecall.app.billing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.securecall.app.R
import com.securecall.app.config.TierManager
import com.securecall.app.net.WebSocketService
import com.securecall.app.ui.EdgeToEdgeHelper

/**
 * Shown after a successful Google Play purchase of an activation code.
 *
 * Sends purchase token to backend for verification, receives activation code,
 * and lets the user copy, share, or instantly activate it.
 */
class PurchaseResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PURCHASE_TOKEN = "purchase_token"
        const val EXTRA_PRODUCT_ID = "product_id"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    private lateinit var tvCode: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnRetry: Button
    private var activationCode: String? = null
    private var purchaseToken: String = ""
    private var productId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this)
        setContentView(R.layout.activity_purchase_result)
        EdgeToEdgeHelper.applySystemBarPaddingToContent(this)

        tvCode = findViewById(R.id.tvActivationCode)
        tvStatus = findViewById(R.id.tvStatus)

        purchaseToken = intent.getStringExtra(EXTRA_PURCHASE_TOKEN) ?: ""
        productId = intent.getStringExtra(EXTRA_PRODUCT_ID) ?: ""
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: this.packageName
        btnRetry = findViewById(R.id.btnRetryVerification)
        btnRetry.setOnClickListener { verifyPurchase() }

        // Copy button
        findViewById<Button>(R.id.btnCopyCode).setOnClickListener {
            val code = activationCode ?: return@setOnClickListener
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Activation Code", code))
            Toast.makeText(this, "Code copied!", Toast.LENGTH_SHORT).show()
        }

        // Share button
        findViewById<Button>(R.id.btnShareCode).setOnClickListener {
            val code = activationCode ?: return@setOnClickListener
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Your SecureCall Premium activation code: $code\n\nEnter it in Settings → Activation Code to activate Premium.")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Activation Code"))
        }

        // Activate Now button
        findViewById<Button>(R.id.btnActivateNow).setOnClickListener {
            val code = activationCode ?: return@setOnClickListener
            activateCode(code)
        }

        // Verify purchase with backend
        if (purchaseToken.isNotEmpty() && productId.isNotEmpty() && packageName == this.packageName) {
            verifyPurchase()
        } else {
            showVerificationError("Invalid purchase details")
        }
    }

    private fun verifyPurchase() {
        tvStatus.text = "Verifying purchase…"
        tvCode.text = "Loading…"
        btnRetry.visibility = android.view.View.GONE
        val ws = WebSocketService.instance
        if (ws == null || !ws.isRegistered) {
            showVerificationError("Connect to SecureCall, then retry")
            return
        }
        ws.verifyPlayOneTimePurchase(purchaseToken, productId) { success, code, tier, error ->
            runOnUiThread {
                if (!success || code.isBlank()) {
                    showVerificationError(if (error.isBlank()) "Verification failed" else error)
                    return@runOnUiThread
                }
                activationCode = code
                tvCode.text = code
                tvStatus.text = "Code generated — ${tier.uppercase()} tier"
                btnRetry.visibility = android.view.View.GONE
            }
        }
    }

    private fun showVerificationError(message: String) {
        tvStatus.text = message
        tvCode.text = "—"
        btnRetry.visibility = android.view.View.VISIBLE
    }

    private fun activateCode(code: String) {
        val ws = WebSocketService.instance
        if (ws == null || !ws.isConnected) {
            Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "Activating…"

        ws.activateCode(code) { success, tier, error ->
            runOnUiThread {
                if (success && tier.isNotEmpty()) {
                    TierManager.setActivatedTier(this, tier)
                    Toast.makeText(this, "Premium activated! Restarting…", Toast.LENGTH_LONG).show()
                    // Restart app
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }, 1500)
                } else {
                    tvStatus.text = "Activation failed: ${error.ifBlank { "unknown" }}"
                }
            }
        }
    }
}

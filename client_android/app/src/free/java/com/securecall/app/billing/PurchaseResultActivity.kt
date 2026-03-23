package com.securecall.app.billing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.securecall.app.R
import com.securecall.app.config.TierManager
import com.securecall.app.net.WebSocketService
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Shown after a successful Google Play purchase of an activation code.
 *
 * Sends purchase token to backend for verification, receives activation code,
 * and lets the user copy, share, or instantly activate it.
 */
class PurchaseResultActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PurchaseResult"
        const val EXTRA_PURCHASE_TOKEN = "purchase_token"
        const val EXTRA_PRODUCT_ID = "product_id"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    private lateinit var tvCode: TextView
    private lateinit var tvStatus: TextView
    private var activationCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_result)

        tvCode = findViewById(R.id.tvActivationCode)
        tvStatus = findViewById(R.id.tvStatus)

        val purchaseToken = intent.getStringExtra(EXTRA_PURCHASE_TOKEN) ?: ""
        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID) ?: ""
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: this.packageName

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
        if (purchaseToken.isNotEmpty()) {
            verifyPurchase(purchaseToken, productId, packageName)
        } else {
            tvStatus.text = "Error: no purchase token"
        }
    }

    private fun verifyPurchase(token: String, productId: String, packageName: String) {
        tvStatus.text = "Verifying purchase…"
        tvCode.text = "Loading…"

        val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_base_url", null)
            ?: com.securecall.app.BuildConfig.SIGNAL_WS_URL
                .replace("wss://", "https://")
                .replace("ws://", "http://")
                .replace("/signal", "")
        val adminKey = prefs.getString("admin_api_key", null) ?: ""

        val json = JSONObject().apply {
            put("purchase_token", token)
            put("product_id", productId)
            put("package_name", packageName)
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$serverUrl/billing/verify-purchase")
            .header("X-Admin-Key", adminKey)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Verification request failed", e)
                runOnUiThread {
                    tvStatus.text = "Verification failed — check connection"
                    tvCode.text = "—"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val result = JSONObject(body)
                        if (response.isSuccessful && result.has("code")) {
                            activationCode = result.getString("code")
                            tvCode.text = activationCode
                            tvStatus.text = "Code generated — ${result.optString("tier", "premium").uppercase()} tier"
                            Log.d(TAG, "Activation code received: $activationCode")
                        } else {
                            val error = result.optString("error", "unknown error")
                            tvStatus.text = "Error: $error"
                            tvCode.text = "—"
                            Log.e(TAG, "Verification error: $error")
                        }
                    } catch (e: Exception) {
                        tvStatus.text = "Parse error"
                        tvCode.text = "—"
                        Log.e(TAG, "Response parse error", e)
                    }
                }
            }
        })
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
                    tvStatus.text = "Activation failed: ${error ?: "unknown"}"
                }
            }
        }
    }
}

package com.securecall.app.wallet

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.securecall.app.config.IfrLockManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * SIWE (Sign-In with Ethereum) wallet verification.
 *
 * Flow:
 * 1. App requests challenge from backend (nonce + message)
 * 2. User copies message → signs in MetaMask → pastes signature
 * 3. Backend verifies: ecrecover(message, signature) == walletAddress
 * 4. Backend checks IFR balance → permanent tier unlock
 *
 * No external SDK needed. Cryptographic proof of wallet ownership.
 */
object WalletConnectManager {

    private const val TAG = "WalletConnect"
    private val WALLET_REGEX = "^0x[0-9a-fA-F]{40}$".toRegex()
    private val SIG_REGEX = "^0x[0-9a-fA-F]{130}$".toRegex()
    private const val BACKEND_URL = "https://protective-healing-production.up.railway.app"

    private val WALLETS = listOf(
        WalletApp("MetaMask", "io.metamask", "https://metamask.app.link"),
        WalletApp("Trust Wallet", "com.wallet.crypto.trustapp", "https://link.trustwallet.com"),
        WalletApp("Rainbow", "me.rainbow", "https://rnbwapp.com"),
        WalletApp("Coinbase Wallet", "org.toshi", "https://go.cb-w.com")
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var connectedAddress: String? = null
        private set

    data class WalletApp(val name: String, val packageName: String, val deepLink: String)

    fun init(application: Application) {
        isInitialized = true
        Log.d(TAG, "Wallet verification ready (SIWE mode)")
    }

    /**
     * Full SIWE flow: wallet chooser → challenge → sign → verify.
     */
    fun connect(context: Context, callback: (Boolean, String) -> Unit) {
        if (context !is Activity) {
            callback(false, "Cannot show dialog")
            return
        }
        val activity = context
        val pm = activity.packageManager

        val installed = WALLETS.filter { isInstalled(pm, it.packageName) }
        val notInstalled = WALLETS.filter { !isInstalled(pm, it.packageName) }

        val items = mutableListOf<String>()
        installed.forEach { items.add("\uD83D\uDFE2 ${it.name}") }
        notInstalled.forEach { items.add("\u26AA ${it.name} (not installed)") }
        items.add("\u270F\uFE0F Enter manually (no signature required)")

        AlertDialog.Builder(activity)
            .setTitle("\uD83D\uDD10 Verify Wallet Ownership")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which < installed.size -> {
                        val wallet = installed[which]
                        startSiweFlow(activity, wallet, callback)
                    }
                    which < installed.size + notInstalled.size -> {
                        Toast.makeText(activity, "Not installed — use manual entry", Toast.LENGTH_SHORT).show()
                        callback(false, "manual_fallback")
                    }
                    else -> {
                        // Manual entry — no SIWE, uses existing manual verify flow
                        callback(false, "manual_fallback")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startSiweFlow(activity: Activity, wallet: WalletApp, callback: (Boolean, String) -> Unit) {
        // Step 1: Get challenge from backend (background thread)
        val prefs = activity.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("client_id", null) ?: "unknown"

        Toast.makeText(activity, "Requesting challenge...", Toast.LENGTH_SHORT).show()

        Thread({
            val challenge = fetchChallenge(deviceId)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (challenge == null) {
                    Toast.makeText(activity, "Failed to get challenge from server", Toast.LENGTH_LONG).show()
                    callback(false, "Challenge request failed")
                    return@runOnUiThread
                }
                // Step 2: Show SIWE dialog
                showSiweDialog(activity, wallet, challenge.first, challenge.second, deviceId, callback)
            }
        }, "siwe-challenge").start()
    }

    private fun fetchChallenge(deviceId: String): Pair<String, String>? { // nonce, message
        return try {
            val url = "$BACKEND_URL/siwe/challenge?deviceId=${Uri.encode(deviceId)}"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val nonce = json.getString("nonce")
            val message = json.getString("message")
            Log.d(TAG, "Challenge received, nonce: ${nonce.take(12)}...")
            Pair(nonce, message)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to fetch challenge: ${e.message}")
            null
        }
    }

    private fun showSiweDialog(
        activity: Activity, wallet: WalletApp,
        nonce: String, message: String, deviceId: String,
        callback: (Boolean, String) -> Unit
    ) {
        val dp = activity.resources.displayMetrics.density
        val pad = (16 * dp).toInt()

        val scroll = ScrollView(activity)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        scroll.addView(layout)

        // Challenge message display
        val msgLabel = TextView(activity).apply {
            text = "\uD83D\uDD12 Sign this message in ${wallet.name}:"
            textSize = 14f
            setTextColor(0xFFDDDDDD.toInt())
        }
        layout.addView(msgLabel)

        val msgBox = TextView(activity).apply {
            text = message
            textSize = 12f
            setTextColor(0xFF999999.toInt())
            setBackgroundColor(0xFF1A1A2E.toInt())
            val p = (8 * dp).toInt()
            setPadding(p, p, p, p)
            setTextIsSelectable(true)
        }
        layout.addView(msgBox)

        // Wallet address input
        val addrLabel = TextView(activity).apply {
            text = "\n\uD83D\uDCCB Wallet Address:"
            textSize = 13f
            setTextColor(0xFFBBBBBB.toInt())
        }
        layout.addView(addrLabel)

        val addrInput = EditText(activity).apply {
            hint = "0x..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 13f
            setSingleLine(true)
        }
        layout.addView(addrInput)

        // Signature input
        val sigLabel = TextView(activity).apply {
            text = "\n\u270D\uFE0F Signature:"
            textSize = 13f
            setTextColor(0xFFBBBBBB.toInt())
        }
        layout.addView(sigLabel)

        val sigInput = EditText(activity).apply {
            hint = "0x... (paste from wallet)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 13f
            setSingleLine(true)
        }
        layout.addView(sigInput)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("\uD83D\uDD10 Sign-In with Ethereum")
            .setView(scroll)
            .setPositiveButton("Verify", null) // set below to prevent auto-dismiss
            .setNeutralButton("Copy Message", null)
            .setNegativeButton("Cancel") { _, _ -> callback(false, "Cancelled") }
            .create()

        dialog.setOnShowListener {
            // Copy Message button
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SIWE Challenge", message))
                Toast.makeText(activity, "Message copied! Now sign it in ${wallet.name}", Toast.LENGTH_SHORT).show()
                openWalletApp(activity, wallet)
            }

            // Verify button
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = addrInput.text.toString().trim()
                val signature = sigInput.text.toString().trim()

                if (!WALLET_REGEX.matches(address)) {
                    Toast.makeText(activity, "Invalid wallet address", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!SIG_REGEX.matches(signature)) {
                    Toast.makeText(activity, "Invalid signature format (must be 0x + 130 hex chars)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "Verifying..."

                Thread({
                    val result = submitSiweVerification(address, signature, nonce, deviceId)
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        if (result != null && result.optBoolean("success")) {
                            val tier = result.optString("tier", "")
                            val amount = result.optString("lockedAmount", "0")
                            connectedAddress = address
                            IfrLockManager.storeVerificationResult(
                                activity, address, tier, amount, IfrLockManager.METHOD_WALLETCONNECT
                            )
                            dialog.dismiss()
                            callback(true, "Unlocked $tier with $amount IFR (permanent, SIWE verified)")
                        } else {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "Verify"
                            val error = result?.optString("error", "unknown") ?: "network_error"
                            val msg = when (error) {
                                "signature_invalid" -> "Signature verification failed — make sure you signed the exact message"
                                "wallet_bound" -> "This wallet is already linked to another device"
                                "insufficient" -> "Insufficient IFR (need 1,000 for Pro / 5,000 for Premium)"
                                "invalid_nonce" -> "Challenge expired — tap Cancel and try again"
                                "challenge_expired" -> "Challenge expired (5 min) — tap Cancel and try again"
                                "balance_check_failed" -> "Ethereum RPC unavailable — try again later"
                                else -> "Verification failed: $error"
                            }
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }, "siwe-verify").start()
            }
        }
        dialog.show()
    }

    private fun submitSiweVerification(
        walletAddress: String, signature: String, nonce: String, deviceId: String
    ): JSONObject? {
        return try {
            val body = JSONObject().apply {
                put("walletAddress", walletAddress)
                put("signature", signature)
                put("nonce", nonce)
                put("deviceId", deviceId)
            }
            val request = Request.Builder()
                .url("$BACKEND_URL/siwe/verify")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.newCall(request).execute()
            val text = response.body?.string() ?: return null
            Log.d(TAG, "SIWE verify response: $text")
            JSONObject(text)
        } catch (e: Throwable) {
            Log.e(TAG, "SIWE verify failed: ${e.message}")
            null
        }
    }

    private fun openWalletApp(activity: Activity, wallet: WalletApp) {
        try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(wallet.packageName)
            if (launchIntent != null) {
                activity.startActivity(launchIntent)
            } else {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(wallet.deepLink)))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to open ${wallet.name}: ${e.message}")
        }
    }

    fun getConnectedWallet(): String? = connectedAddress

    fun disconnect(context: Context) {
        connectedAddress = null
        Log.d(TAG, "Wallet disconnected")
    }

    /**
     * Legacy verify (non-SIWE) — used by SettingsFragment for already-connected wallets.
     */
    fun verifyAndUnlock(context: Context, walletAddress: String, callback: (Boolean, String) -> Unit) {
        try {
            val ws = com.securecall.app.net.WebSocketService.instance
            if (ws == null || !ws.isConnected) {
                callback(false, "Not connected to server")
                return
            }
            ws.verifyIfrLock(walletAddress) { success, tier, amount, error ->
                if (success && tier.isNotEmpty()) {
                    IfrLockManager.storeVerificationResult(
                        context, walletAddress, tier, amount, IfrLockManager.METHOD_WALLETCONNECT
                    )
                    connectedAddress = walletAddress
                    callback(true, "Unlocked $tier with $amount IFR (permanent)")
                } else {
                    val msg = when (error) {
                        "insufficient" -> "Insufficient IFR balance ($amount held)"
                        "wallet_bound" -> "This wallet is already linked to another device"
                        else -> "Verification failed: $error"
                    }
                    callback(false, msg)
                }
            }
        } catch (e: Throwable) {
            callback(false, "Verification failed: ${e.message}")
        }
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean {
        return try { pm.getPackageInfo(packageName, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }
    }
}

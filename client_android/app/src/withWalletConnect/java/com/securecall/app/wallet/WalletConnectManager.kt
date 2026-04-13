package com.securecall.app.wallet

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.securecall.app.config.IfrLockManager

/**
 * Wallet connection for IFR token verification via deep links.
 *
 * No external SDK needed — opens wallet apps directly and lets the user
 * paste their address. Backend verifies IFR balance on Ethereum.
 *
 * Replaces Reown AppKit which had unresolved relay 403 bug (reown-kotlin #240).
 */
object WalletConnectManager {

    private const val TAG = "WalletConnect"
    private val WALLET_REGEX = "^0x[0-9a-fA-F]{40}$".toRegex()

    private val WALLETS = listOf(
        WalletApp("MetaMask", "io.metamask", "https://metamask.app.link"),
        WalletApp("Trust Wallet", "com.wallet.crypto.trustapp", "https://link.trustwallet.com"),
        WalletApp("Rainbow", "me.rainbow", "https://rnbwapp.com"),
        WalletApp("Coinbase Wallet", "org.toshi", "https://go.cb-w.com")
    )

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var connectedAddress: String? = null
        private set

    data class WalletApp(val name: String, val packageName: String, val deepLink: String)

    fun init(application: Application) {
        isInitialized = true
        Log.d(TAG, "Wallet connect ready (deep link mode — no external SDK)")
    }

    /**
     * Show wallet chooser dialog, open selected wallet, then prompt for address paste.
     */
    fun connect(context: Context, callback: (Boolean, String) -> Unit) {
        if (context !is Activity) {
            callback(false, "Cannot show dialog — not an Activity context")
            return
        }
        val activity = context
        val pm = activity.packageManager

        // Build list: installed wallets first, then "Enter manually"
        val installed = WALLETS.filter { isInstalled(pm, it.packageName) }
        val notInstalled = WALLETS.filter { !isInstalled(pm, it.packageName) }

        val items = mutableListOf<String>()
        installed.forEach { items.add("\uD83D\uDFE2 ${it.name}") }  // green dot = installed
        notInstalled.forEach { items.add("\u26AA ${it.name} (not installed)") }
        items.add("\u270F\uFE0F Enter wallet address manually")

        AlertDialog.Builder(activity)
            .setTitle("\uD83D\uDD17 Connect Wallet")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which < installed.size -> {
                        // Open installed wallet, then show paste dialog
                        val wallet = installed[which]
                        openWalletApp(activity, wallet)
                        // Show paste dialog after a short delay (user switches to wallet, copies address, comes back)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                showPasteDialog(activity, wallet.name, callback)
                            }
                        }, 1500)
                    }
                    which < installed.size + notInstalled.size -> {
                        // Not installed — still show paste dialog (user might have address from another source)
                        val wallet = notInstalled[which - installed.size]
                        Toast.makeText(activity, "${wallet.name} not installed — enter address manually", Toast.LENGTH_SHORT).show()
                        showPasteDialog(activity, null, callback)
                    }
                    else -> {
                        // Manual entry
                        showPasteDialog(activity, null, callback)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openWalletApp(activity: Activity, wallet: WalletApp) {
        try {
            // Try launching the wallet app directly
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(wallet.packageName)
            if (launchIntent != null) {
                activity.startActivity(launchIntent)
                Log.d(TAG, "Opened ${wallet.name} via launch intent")
            } else {
                // Fallback to deep link
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(wallet.deepLink)))
                Log.d(TAG, "Opened ${wallet.name} via deep link")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to open ${wallet.name}: ${e.message}")
        }
    }

    private fun showPasteDialog(activity: Activity, walletName: String?, callback: (Boolean, String) -> Unit) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        val input = EditText(activity).apply {
            hint = "0x..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 14f
            setSingleLine(true)
        }
        layout.addView(input)

        val msg = if (walletName != null) {
            "Open $walletName → copy your wallet address → paste here:"
        } else {
            "Paste your Ethereum wallet address (0x...):"
        }

        AlertDialog.Builder(activity)
            .setTitle("\uD83D\uDCCB Paste Wallet Address")
            .setMessage(msg)
            .setView(layout)
            .setPositiveButton("Verify") { _, _ ->
                val address = input.text.toString().trim()
                if (WALLET_REGEX.matches(address)) {
                    connectedAddress = address
                    Log.d(TAG, "Wallet address entered: ${address.take(6)}...${address.takeLast(4)}")
                    callback(true, address)
                } else {
                    Toast.makeText(activity, "Invalid address — must be 0x followed by 40 hex characters", Toast.LENGTH_LONG).show()
                    callback(false, "Invalid wallet address format")
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                callback(false, "Cancelled")
            }
            .show()
    }

    fun getConnectedWallet(): String? = connectedAddress

    fun disconnect(context: Context) {
        connectedAddress = null
        Log.d(TAG, "Wallet disconnected")
    }

    /**
     * Verify IFR token balance via backend and unlock tier.
     * Uses METHOD_WALLETCONNECT for permanent unlock (no 30-day expiry).
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
                        context, walletAddress, tier, amount,
                        IfrLockManager.METHOD_WALLETCONNECT
                    )
                    connectedAddress = walletAddress
                    Log.d(TAG, "Verified: $amount IFR → $tier (permanent)")
                    callback(true, "Unlocked $tier with $amount IFR (permanent)")
                } else {
                    val msg = when (error) {
                        "insufficient" -> "Insufficient IFR balance ($amount held)"
                        "wallet_bound" -> "This wallet is already linked to another device"
                        "not_connected" -> "Server not connected"
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
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

package com.securecall.app.wallet

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.securecall.app.R
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
 * 2. User signs in MetaMask / wallet browser
 * 3. Wallet redirects back to https://stealthx.tech/return/securecall with address + signature
 * 4. Backend verifies: ecrecover(message, signature) == walletAddress
 * 5. Backend checks IFR balance → permanent tier unlock
 *
 * No external SDK needed. Cryptographic proof of wallet ownership.
 */
object WalletConnectManager {

    private const val TAG = "WalletConnect"
    private val WALLET_REGEX = "^0x[0-9a-fA-F]{40}$".toRegex()
    private val SIG_REGEX = "^0x[0-9a-fA-F]{130}$".toRegex()
    private const val BACKEND_URL = "https://api.stealthx.tech"
    private const val RETURN_CHANNEL_ID = "wallet_return"
    private const val RETURN_NOTIFICATION_ID = 9042

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

    @Volatile
    private var pendingCallback: ((Boolean, String) -> Unit)? = null

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

        AlertDialog.Builder(activity)
            .setTitle("\uD83D\uDD10 Verify Wallet Ownership")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which < installed.size -> {
                        val wallet = installed[which]
                        startSiweFlow(activity, wallet, callback)
                    }
                    which < installed.size + notInstalled.size -> {
                        Toast.makeText(activity, "Install ${notInstalled[which - installed.size].name} to connect this wallet", Toast.LENGTH_SHORT).show()
                        callback(false, "Wallet app not installed")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startSiweFlow(activity: Activity, wallet: WalletApp, callback: (Boolean, String) -> Unit) {
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
                val nonce = challenge.first
                val message = challenge.second

                // Open signing page in wallet's built-in dApp browser. The page returns
                // through the verified https app link after the wallet signs the challenge.
                val encodedMsg = Uri.encode(message)
                val encodedDevice = Uri.encode(deviceId)
                val encodedPackage = Uri.encode(activity.packageName)
                val dappPath = "stealthx.tech/siwe.html?nonce=$nonce&deviceId=$encodedDevice&message=$encodedMsg&returnScheme=securecall&returnHost=wc&returnPackage=$encodedPackage&ts=${System.currentTimeMillis()}"
                val pageUrl = "https://$dappPath"

                // Each wallet has a different deep link format for its in-app browser
                val mmDeepLink = when (wallet.packageName) {
                    "io.metamask" -> "https://metamask.app.link/dapp/$dappPath"
                    "com.wallet.crypto.trustapp" -> "https://link.trustwallet.com/open_url?coin_id=60&url=${Uri.encode(pageUrl)}"
                    else -> pageUrl
                }

                pendingCallback = callback
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mmDeepLink)))
                    Log.d(TAG, "Opened signing page in ${wallet.name} browser")
                    startStatusPolling(activity.applicationContext, deviceId)
                } catch (e: Throwable) {
                    Log.w(TAG, "Deep link failed, opening in default browser: ${e.message}")
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)))
                    startStatusPolling(activity.applicationContext, deviceId)
                }

                Toast.makeText(activity, "Sign the challenge in your wallet. SecureCall will return automatically.", Toast.LENGTH_LONG).show()
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

    private fun fetchSiweStatus(deviceId: String): JSONObject? {
        return try {
            val url = "$BACKEND_URL/siwe/status?deviceId=${Uri.encode(deviceId)}"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            JSONObject(response.body?.string() ?: return null)
        } catch (e: Throwable) {
            Log.d(TAG, "SIWE status poll failed: ${e.message}")
            null
        }
    }

    private fun startStatusPolling(context: Context, deviceId: String) {
        Thread({
            repeat(150) {
                Thread.sleep(2000)
                val status = fetchSiweStatus(deviceId) ?: return@repeat
                if (!status.optBoolean("verified", false)) return@repeat

                val address = status.optString("walletAddress", "")
                if (!WALLET_REGEX.matches(address)) return@repeat

                val pending = pendingCallback ?: return@Thread
                val result = applySiweResult(context, address, status)
                pendingCallback = null
                pending.invoke(result.first, result.second)
                showReturnNotification(context, result.second)
                Log.d(TAG, "SIWE status confirmed in background: $address")
                return@Thread
            }
        }, "siwe-status-poll").start()
    }

    private fun showReturnNotification(context: Context, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    RETURN_CHANNEL_ID,
                    "Wallet return",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            RETURN_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, RETURN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("SecureCall wallet verified")
            .setContentText("Tap to return to SecureCall")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(RETURN_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot show wallet return notification: ${e.message}")
        }
    }

    fun handleDeepLink(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        val isCustomCallback = uri.scheme == "securecall" && uri.host == "wc"
        val isHttpsCallback = uri.scheme == "https" && uri.host == "stealthx.tech" &&
            (uri.path?.startsWith("/return/securecall") == true ||
                (uri.path?.startsWith("/return") == true &&
                    (uri.getQueryParameter("app") ?: "securecall") == "securecall")) &&
            (uri.getQueryParameter("app") ?: "securecall") == "securecall"
        if (!isCustomCallback && !isHttpsCallback) return false

        val address = uri.getQueryParameter("address")
            ?: uri.getQueryParameter("walletAddress")
            ?: uri.getQueryParameter("addr")
        val signature = uri.getQueryParameter("signature")
            ?: uri.getQueryParameter("sig")
        val nonce = uri.getQueryParameter("nonce")
        val deviceId = uri.getQueryParameter("deviceId")
        if (address == null || !WALLET_REGEX.matches(address) ||
            signature == null || !SIG_REGEX.matches(signature) ||
            nonce.isNullOrBlank() || deviceId.isNullOrBlank()
        ) {
            pendingCallback?.invoke(false, "Wallet callback was incomplete")
            Toast.makeText(context, "Wallet callback was incomplete", Toast.LENGTH_LONG).show()
            return true
        }

        Thread({
            val result = submitSiweVerification(address, signature, nonce, deviceId)
            val callback = pendingCallback
            pendingCallback = null
            val message = applySiweResult(context, address, result)
            callback?.invoke(message.first, message.second)
            if (context is Activity) {
                context.runOnUiThread {
                    Toast.makeText(context, message.second, Toast.LENGTH_LONG).show()
                }
            }
        }, "siwe-callback-verify").start()
        return true
    }

    private fun applySiweResult(context: Context, address: String, result: JSONObject?): Pair<Boolean, String> {
        if (result != null && result.optBoolean("success")) {
            val tier = result.optString("tier", "")
            val amount = result.optString("balanceAmount", result.optString("lockedAmount", "0"))
            connectedAddress = address
            IfrLockManager.storeVerificationResult(
                context, address, tier, amount, IfrLockManager.METHOD_WALLETCONNECT
            )
            return true to "Unlocked $tier with $amount IFR (permanent, wallet verified)"
        }

        val error = result?.optString("error", "unknown") ?: "network_error"
        val walletBound = result?.optBoolean("walletBound", false) ?: false
        val amount = result?.optString("balanceAmount", result.optString("lockedAmount", "0")) ?: "0"
        if (error == "insufficient" && walletBound) {
            connectedAddress = address
            val prefs = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("ifr_wallet_address", address.lowercase())
                .putString("ifr_locked_amount", amount)
                .putString("ifr_verification_method", IfrLockManager.METHOD_WALLETCONNECT)
                .putLong("ifr_last_verified", System.currentTimeMillis())
                .putLong("ifr_wallet_verified_at", System.currentTimeMillis())
                .apply()
            return true to "Wallet verified & connected ($amount IFR held).\nNeed 2,000 IFR for Pro / 6,000 for Premium."
        }

        val msg = when (error) {
            "signature_invalid" -> "Signature verification failed — did you sign the exact message?"
            "wallet_bound" -> "This wallet is already linked to another device"
            "invalid_nonce", "challenge_expired" -> "Challenge expired — try again"
            "balance_check_failed" -> "Ethereum RPC unavailable — try again later"
            else -> "Verification failed: $error"
        }
        return false to msg
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
            if (ws == null || !ws.isConnected || !ws.isRegistered) {
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

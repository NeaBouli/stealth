package com.securecall.app.config

import android.content.Context
import android.util.Log

/**
 * Manages IFR token lock verification for tier unlocking.
 * Users lock IFR tokens in the IFRLock smart contract on Ethereum.
 * 1,000 IFR locked → Pro, 5,000 IFR locked → Premium.
 */
object IfrLockManager {
    private const val TAG = "IFR_LOCK"
    private const val PREFS = "securecall_prefs"
    private const val KEY_WALLET = "ifr_wallet_address"
    private const val KEY_IFR_TIER = "ifr_tier"
    private const val KEY_IFR_AMOUNT = "ifr_locked_amount"
    private const val KEY_LAST_VERIFIED = "ifr_last_verified"
    private const val REVERIFY_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours

    fun getWalletAddress(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WALLET, null)
    }

    fun getIfrTier(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IFR_TIER, null)
    }

    fun getLockedAmount(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IFR_AMOUNT, "0") ?: "0"
    }

    fun needsReverification(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_VERIFIED, 0)
        return System.currentTimeMillis() - last > REVERIFY_INTERVAL
    }

    fun storeVerificationResult(context: Context, wallet: String, tier: String, amount: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_WALLET, wallet.lowercase())
            .putString(KEY_IFR_TIER, tier)
            .putString(KEY_IFR_AMOUNT, amount)
            .putLong(KEY_LAST_VERIFIED, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Stored IFR verification: wallet=$wallet, tier=$tier, amount=$amount IFR")

        // Update TierManager with IFR-based tier
        TierManager.setActivatedTier(context, tier)
    }

    fun clearIfrUnlock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_WALLET)
            .remove(KEY_IFR_TIER)
            .remove(KEY_IFR_AMOUNT)
            .remove(KEY_LAST_VERIFIED)
            .apply()
        Log.d(TAG, "IFR unlock cleared")
    }

    /**
     * Verify IFR lock via WebSocket → server → Ethereum.
     * Callback: (success, tier, lockedAmount, error)
     */
    fun verify(context: Context, walletAddress: String, callback: (Boolean, String, String, String) -> Unit) {
        val ws = com.securecall.app.net.WebSocketService.instance
        if (ws == null || !ws.isConnected) {
            callback(false, "", "0", "not_connected")
            return
        }
        ws.verifyIfrLock(walletAddress) { success, tier, amount, error ->
            if (success && tier.isNotEmpty()) {
                storeVerificationResult(context, walletAddress, tier, amount)
            }
            callback(success, tier, amount, error)
        }
    }

    /**
     * Periodic re-verification: called on app startup.
     * If wallet is stored and reverification is due, check lock status.
     * If lock no longer meets threshold, revert tier.
     */
    fun reverifyIfNeeded(context: Context) {
        val wallet = getWalletAddress(context) ?: return
        if (!needsReverification(context)) return

        Log.d(TAG, "Re-verifying IFR lock for $wallet")
        verify(context, wallet) { success, tier, _, error ->
            if (!success) {
                Log.w(TAG, "IFR re-verification failed ($error) — reverting tier")
                clearIfrUnlock(context)
                // Revert to build flavor tier
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove("activated_tier").apply()
            }
        }
    }
}

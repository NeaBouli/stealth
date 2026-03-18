package com.securecall.app.config

import android.content.Context
import android.util.Log

/**
 * Manages IFR token verification for tier unlocking.
 * Users hold IFR tokens on their Ethereum wallet.
 * 1,000 IFR = Pro, 5,000 IFR = Premium.
 *
 * Manual wallet entry: expires after 30 days. One wallet per device.
 * WalletConnect: no expiration, unlimited devices (coming soon).
 */
object IfrLockManager {
    private const val TAG = "IFR_LOCK"
    private const val PREFS = "securecall_prefs"
    private const val KEY_WALLET = "ifr_wallet_address"
    private const val KEY_IFR_TIER = "ifr_tier"
    private const val KEY_IFR_AMOUNT = "ifr_locked_amount"
    private const val KEY_LAST_VERIFIED = "ifr_last_verified"
    private const val KEY_VERIFIED_AT = "ifr_wallet_verified_at"
    private const val KEY_VERIFICATION_METHOD = "ifr_verification_method"
    private const val REVERIFY_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
    private const val MANUAL_EXPIRY_DAYS = 30
    private const val MANUAL_EXPIRY_MS = MANUAL_EXPIRY_DAYS * 24 * 60 * 60 * 1000L

    const val METHOD_MANUAL = "manual"
    const val METHOD_WALLETCONNECT = "walletconnect"

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

    fun getVerificationMethod(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_VERIFICATION_METHOD, METHOD_MANUAL) ?: METHOD_MANUAL
    }

    fun getVerifiedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_VERIFIED_AT, 0)
    }

    /** Days remaining for manual wallet. -1 if WalletConnect (no expiry). */
    fun getDaysRemaining(context: Context): Int {
        if (getVerificationMethod(context) == METHOD_WALLETCONNECT) return -1
        val verifiedAt = getVerifiedAt(context)
        if (verifiedAt == 0L) return 0
        val elapsed = System.currentTimeMillis() - verifiedAt
        val remaining = MANUAL_EXPIRY_MS - elapsed
        return if (remaining > 0) (remaining / (24 * 60 * 60 * 1000L)).toInt() else 0
    }

    /** True if manual wallet has expired (>30 days). */
    fun isManualExpired(context: Context): Boolean {
        if (getVerificationMethod(context) == METHOD_WALLETCONNECT) return false
        val verifiedAt = getVerifiedAt(context)
        if (verifiedAt == 0L) return false
        return System.currentTimeMillis() - verifiedAt > MANUAL_EXPIRY_MS
    }

    fun needsReverification(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_VERIFIED, 0)
        return System.currentTimeMillis() - last > REVERIFY_INTERVAL
    }

    fun storeVerificationResult(context: Context, wallet: String, tier: String, amount: String, method: String = METHOD_MANUAL) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_WALLET, wallet.lowercase())
            .putString(KEY_IFR_TIER, tier)
            .putString(KEY_IFR_AMOUNT, amount)
            .putLong(KEY_LAST_VERIFIED, now)
            .putString(KEY_VERIFICATION_METHOD, method)

        // Only set verified_at on first verification (don't reset the 30-day clock on re-verify)
        if (prefs.getLong(KEY_VERIFIED_AT, 0) == 0L) {
            editor.putLong(KEY_VERIFIED_AT, now)
        }
        editor.apply()

        Log.d(TAG, "Stored IFR verification: wallet=$wallet, tier=$tier, amount=$amount IFR, method=$method")
        TierManager.setActivatedTier(context, tier)
    }

    fun clearIfrUnlock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_WALLET)
            .remove(KEY_IFR_TIER)
            .remove(KEY_IFR_AMOUNT)
            .remove(KEY_LAST_VERIFIED)
            .remove(KEY_VERIFIED_AT)
            .remove(KEY_VERIFICATION_METHOD)
            .apply()
        Log.d(TAG, "IFR unlock cleared")
    }

    fun verify(context: Context, walletAddress: String, callback: (Boolean, String, String, String) -> Unit) {
        val ws = com.securecall.app.net.WebSocketService.instance
        if (ws == null || !ws.isConnected) {
            callback(false, "", "0", "not_connected")
            return
        }
        ws.verifyIfrLock(walletAddress) { success, tier, amount, error ->
            if (success && tier.isNotEmpty()) {
                storeVerificationResult(context, walletAddress, tier, amount, METHOD_MANUAL)
            } else if (amount != "0" && amount.isNotEmpty()) {
                // Store the amount even on insufficient balance so the UI can show it.
                // Don't store the tier — user doesn't get an upgrade.
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_WALLET, walletAddress.lowercase())
                    .putString(KEY_IFR_AMOUNT, amount)
                    .putLong(KEY_LAST_VERIFIED, System.currentTimeMillis())
                    .putString(KEY_VERIFICATION_METHOD, METHOD_MANUAL)
                    .apply()
                Log.d(TAG, "Stored IFR balance (insufficient for tier): wallet=$walletAddress, amount=$amount IFR")
            }
            callback(success, tier, amount, error)
        }
    }

    /**
     * Called on every app startup.
     * 1. Check 30-day expiration for manual wallets
     * 2. Re-verify balance every 24h
     * 3. Revert tier if balance insufficient or expired
     */
    fun reverifyIfNeeded(context: Context) {
        val wallet = getWalletAddress(context) ?: return
        val method = getVerificationMethod(context)

        // Check 30-day expiration for manual wallets
        if (method == METHOD_MANUAL && isManualExpired(context)) {
            Log.w(TAG, "Manual wallet verification expired after $MANUAL_EXPIRY_DAYS days — reverting tier")
            clearIfrUnlock(context)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("activated_tier").apply()
            return
        }

        if (!needsReverification(context)) return

        Log.d(TAG, "Re-verifying IFR balance for $wallet (method=$method)")
        val ws = com.securecall.app.net.WebSocketService.instance
        if (ws == null || !ws.isConnected) return

        ws.verifyIfrLock(wallet) { success, tier, amount, _ ->
            if (success && tier.isNotEmpty()) {
                // Update balance + last verified (but keep original verified_at timestamp)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_IFR_TIER, tier)
                    .putString(KEY_IFR_AMOUNT, amount)
                    .putLong(KEY_LAST_VERIFIED, System.currentTimeMillis())
                    .apply()
                TierManager.setActivatedTier(context, tier)
                Log.d(TAG, "Re-verification OK: $amount IFR → $tier")
            } else {
                Log.w(TAG, "IFR re-verification: insufficient balance — reverting tier")
                clearIfrUnlock(context)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove("activated_tier").apply()
            }
        }
    }
}

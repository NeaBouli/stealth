package com.securecall.app.wallet

import android.app.Application
import android.content.Context
import android.util.Log

/**
 * F-Droid stub — wallet connection uses activation codes instead.
 */
object WalletConnectManager {

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var connectedAddress: String? = null
        private set

    fun init(application: Application) {
        isInitialized = true
        Log.d("WalletConnect", "F-Droid build — wallet connect via activation codes only")
    }

    fun connect(context: Context, callback: (Boolean, String) -> Unit) {
        callback(false, "Wallet connect is not available in the F-Droid edition.\nUse an activation code or enter your wallet address manually above.")
    }

    fun getConnectedWallet(): String? = null
    fun disconnect(context: Context) { connectedAddress = null }

    fun verifyAndUnlock(context: Context, walletAddress: String, callback: (Boolean, String) -> Unit) {
        callback(false, "Not available in F-Droid edition")
    }
}

package com.securecall.app.wallet

import android.app.Application
import android.content.Context
import android.util.Log

/**
 * F-Droid stub — WalletConnect is not available in the fdroid flavor
 * because the WalletConnect SDK contains proprietary Firebase type references.
 * Users can unlock via activation code instead.
 */
object WalletConnectManager {

    private const val TAG = "WalletConnect"

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var connectedAddress: String? = null
        private set

    fun init(application: Application) {
        Log.d(TAG, "WalletConnect not available in F-Droid build")
    }

    fun connect(context: Context, callback: (Boolean, String) -> Unit) {
        callback(false, "WalletConnect is not available in the F-Droid edition. Use an activation code to unlock.")
    }

    fun getConnectedWallet(): String? = null

    fun disconnect(context: Context) {}

    fun verifyAndUnlock(context: Context, walletAddress: String, callback: (Boolean, String) -> Unit) {
        callback(false, "WalletConnect is not available in the F-Droid edition")
    }
}

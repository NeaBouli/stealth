package com.securecall.app.wallet

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import com.securecall.app.config.IfrLockManager

/**
 * WalletConnect v2 integration via Sign protocol.
 * Connects user's Ethereum wallet to verify IFR token holdings.
 * WalletConnect-verified wallets get permanent tier unlock (no 30-day expiry).
 */
object WalletConnectManager {

    private const val TAG = "WalletConnect"
    private const val PROJECT_ID = "32f56abaa4b1d7f59fb1571c0c0a551f"

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var connectedAddress: String? = null
        private set

    @Volatile
    private var pendingPairingUri: String? = null

    private var connectCallback: ((Boolean, String) -> Unit)? = null

    /**
     * Initialize WalletConnect Core + SignClient. Must be called from Application.onCreate().
     */
    fun init(application: Application) {
        try {
            val metadata = Core.Model.AppMetaData(
                name = "SecureCall",
                description = "Encrypted P2P calling app — verify IFR token holdings",
                url = "https://github.com/NeaBouli/stealth",
                icons = listOf("https://raw.githubusercontent.com/NeaBouli/stealth/main/website/icon.png"),
                redirect = "securecall://wc"
            )

            CoreClient.initialize(
                relayServerUrl = "wss://relay.walletconnect.com?projectId=$PROJECT_ID",
                connectionType = ConnectionType.AUTOMATIC,
                application = application,
                metaData = metadata
            ) { error ->
                val msg = error.throwable.message ?: "unknown"
                Log.e(TAG, "CoreClient init error: $msg")
                if (msg.contains("403")) {
                    Log.e(TAG, "Project ID rejected — register at cloud.reown.com")
                }
            }

            val signParams = Sign.Params.Init(core = CoreClient)
            SignClient.initialize(signParams) { error ->
                Log.e(TAG, "SignClient init error: ${error.throwable.message}")
            }

            SignClient.setDappDelegate(object : SignClient.DappDelegate {
                override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
                    val accounts = approvedSession.accounts
                    if (accounts.isNotEmpty()) {
                        // Account format: "eip155:1:0x1234..."
                        val address = accounts.first().substringAfterLast(":")
                        connectedAddress = address
                        Log.d(TAG, "Session approved — wallet: $address")
                        connectCallback?.invoke(true, address)
                        connectCallback = null
                    }
                }

                override fun onSessionRejected(rejectedSession: Sign.Model.RejectedSession) {
                    Log.w(TAG, "Session rejected: ${rejectedSession.reason}")
                    connectedAddress = null
                    connectCallback?.invoke(false, "Wallet rejected connection")
                    connectCallback = null
                }

                override fun onSessionUpdate(updatedSession: Sign.Model.UpdatedSession) {
                    Log.d(TAG, "Session updated")
                }

                override fun onSessionEvent(sessionEvent: Sign.Model.SessionEvent) {
                    Log.d(TAG, "Session event: ${sessionEvent.name}")
                }

                override fun onSessionExtend(session: Sign.Model.Session) {
                    Log.d(TAG, "Session extended")
                }

                override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
                    Log.d(TAG, "Session deleted")
                    connectedAddress = null
                }

                override fun onSessionRequestResponse(response: Sign.Model.SessionRequestResponse) {
                    Log.d(TAG, "Session request response")
                }

                override fun onProposalExpired(proposal: Sign.Model.ExpiredProposal) {
                    Log.d(TAG, "Proposal expired")
                }

                override fun onRequestExpired(request: Sign.Model.ExpiredRequest) {
                    Log.d(TAG, "Request expired")
                }

                override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
                    Log.d(TAG, "Connection state: ${state.isAvailable}")
                }

                override fun onError(error: Sign.Model.Error) {
                    Log.e(TAG, "Sign error: ${error.throwable.message}")
                }
            })

            isInitialized = true
            Log.d(TAG, "WalletConnect initialized with project $PROJECT_ID")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WalletConnect: ${e.message}", e)
        } catch (e: NoClassDefFoundError) {
            Log.e(TAG, "WalletConnect missing dependency (PushClient) — init skipped: ${e.message}")
        }
    }

    /**
     * Start a new connection. Returns a WalletConnect URI to open in a wallet app.
     */
    fun connect(context: Context, callback: (Boolean, String) -> Unit) {
        if (!isInitialized) {
            callback(false, "WalletConnect not initialized")
            return
        }

        connectCallback = callback

        val namespaces = mapOf(
            "eip155" to Sign.Model.Namespace.Proposal(
                chains = listOf("eip155:1"), // Ethereum mainnet
                methods = listOf("eth_sendTransaction", "personal_sign"),
                events = listOf("chainChanged", "accountsChanged")
            )
        )

        val pairing = CoreClient.Pairing.create { error ->
            val msg = error.throwable.message ?: "unknown"
            Log.e(TAG, "Pairing create error: $msg")
            if (msg.contains("403") || msg.contains("Forbidden")) {
                callback(false, "Invalid WalletConnect Project ID — register at cloud.reown.com")
            } else {
                callback(false, "Pairing failed: $msg")
            }
        } ?: run {
            callback(false, "Failed to create pairing")
            return
        }

        pendingPairingUri = pairing.uri
        Log.d(TAG, "Pairing URI generated")

        val connectParams = Sign.Params.Connect(
            namespaces = namespaces,
            optionalNamespaces = null,
            properties = null,
            pairing = pairing
        )

        SignClient.connect(
            connect = connectParams,
            onSuccess = { _: String ->
                Log.d(TAG, "Connect request sent")

                // Try to open in any installed wallet app
                try {
                    val wcIntent = Intent(Intent.ACTION_VIEW, Uri.parse(pairing.uri))
                    wcIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(wcIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "No wallet app found for WC URI, showing manual option")
                    callback(false, "no_wallet_app")
                }
            },
            onError = { error: Sign.Model.Error ->
                val msg = error.throwable.message ?: "unknown"
                Log.e(TAG, "Connect error: $msg")
                if (msg.contains("403") || msg.contains("Forbidden") || msg.contains("Connection error")) {
                    callback(false, "WalletConnect Project ID not registered — contact developer")
                } else {
                    callback(false, "Connection failed: $msg")
                }
            }
        )
    }

    /**
     * Get current session's wallet address, if any.
     */
    fun getConnectedWallet(): String? {
        if (!isInitialized) return connectedAddress
        return try {
            val sessions = SignClient.getListOfActiveSessions()
            if (sessions.isNotEmpty()) {
                val accounts = sessions.first().namespaces.values
                    .flatMap { it.accounts }
                if (accounts.isNotEmpty()) {
                    accounts.first().substringAfterLast(":")
                } else null
            } else connectedAddress
        } catch (e: Exception) {
            Log.w(TAG, "Error getting session: ${e.message}")
            connectedAddress
        }
    }

    /**
     * Disconnect current session.
     */
    fun disconnect(context: Context) {
        try {
            val sessions = SignClient.getListOfActiveSessions()
            if (sessions.isNotEmpty()) {
                val topic = sessions.first().topic
                SignClient.disconnect(
                    Sign.Params.Disconnect(topic),
                    onSuccess = {
                        connectedAddress = null
                        Log.d(TAG, "Disconnected successfully")
                    },
                    onError = { error ->
                        Log.e(TAG, "Disconnect error: ${error.throwable.message}")
                        connectedAddress = null
                    }
                )
            } else {
                connectedAddress = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect failed: ${e.message}")
            connectedAddress = null
        }
    }

    /**
     * After wallet is connected, verify IFR token balance via the signaling server
     * and store the result with METHOD_WALLETCONNECT (no 30-day expiry).
     */
    fun verifyAndUnlock(context: Context, walletAddress: String, callback: (Boolean, String) -> Unit) {
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
                Log.d(TAG, "WalletConnect verification: $amount IFR → $tier (permanent)")
                callback(true, "Unlocked $tier with $amount IFR (permanent)")
            } else {
                val msg = when (error) {
                    "insufficient" -> "Insufficient IFR balance ($amount held)"
                    "not_connected" -> "Server not connected"
                    else -> "Verification failed: $error"
                }
                callback(false, msg)
            }
        }
    }
}

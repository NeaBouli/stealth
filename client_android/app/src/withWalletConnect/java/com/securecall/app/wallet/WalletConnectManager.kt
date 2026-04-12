package com.securecall.app.wallet

import android.app.Application
import android.content.Context
import android.util.Log
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.securecall.app.config.IfrLockManager

/**
 * Reown AppKit integration for IFR token wallet verification.
 * Replaces WalletConnect SDK 2.x (PushClient/relay auth issues).
 *
 * WalletConnect-verified wallets get permanent tier unlock (no 30-day expiry).
 */
object WalletConnectManager {

    private const val TAG = "WalletConnect"
    // Reown Cloud Project ID — same as Inferno (ifrunit.tech) project.
    // Dashboard: https://cloud.reown.com/app/83571cb4-8aa5-4b4b-bc0e-b9b098785fc7
    // REQUIRED: Add Android platform with allowed app IDs:
    //   com.securecall.app.free, com.securecall.app.pro, com.securecall.app.premium
    private const val PROJECT_ID = "83571cb4-8aa5-4b4b-bc0e-b9b098785fc7"

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var connectedAddress: String? = null
        private set

    @Volatile
    private var relayConnected = false

    private var connectCallback: ((Boolean, String) -> Unit)? = null

    /**
     * Initialize Reown AppKit. Must be called from Application.onCreate().
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
                Log.e(TAG, "CoreClient init error: ${error.throwable.message}")
            }

            AppKit.initialize(
                init = Modal.Params.Init(CoreClient),
                onSuccess = {
                    Log.d(TAG, "AppKit initialized successfully")
                },
                onError = { error ->
                    Log.e(TAG, "AppKit init error: ${error.throwable.message}")
                }
            )

            AppKit.setDelegate(object : AppKit.ModalDelegate {
                override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
                    // Extract wallet address from WalletConnect session
                    val wcSession = approvedSession as? Modal.Model.ApprovedSession.WalletConnectSession
                    val accounts = wcSession?.accounts ?: emptyList()
                    if (accounts.isNotEmpty()) {
                        // Account format: "eip155:1:0x1234..."
                        val address = accounts.first().substringAfterLast(":")
                        connectedAddress = address
                        Log.d(TAG, "Session approved — wallet: $address")
                        connectCallback?.invoke(true, address)
                        connectCallback = null
                    } else {
                        // Coinbase or other session — try getAccount()
                        val account = try { AppKit.getAccount() } catch (_: Throwable) { null }
                        if (account != null) {
                            connectedAddress = account.address
                            Log.d(TAG, "Session approved (non-WC) — wallet: ${account.address}")
                            connectCallback?.invoke(true, account.address)
                            connectCallback = null
                        }
                    }
                }

                override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
                    Log.w(TAG, "Session rejected: ${rejectedSession.reason}")
                    connectedAddress = null
                    connectCallback?.invoke(false, "Wallet rejected connection")
                    connectCallback = null
                }

                override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {}
                override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {}
                override fun onSessionEvent(event: Modal.Model.Event) {}
                override fun onSessionExtend(session: Modal.Model.Session) {}

                override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
                    Log.d(TAG, "Session deleted")
                    connectedAddress = null
                }

                override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {}
                override fun onSessionAuthenticateResponse(response: Modal.Model.SessionAuthenticateResponse) {}
                override fun onSIWEAuthenticationResponse(response: Modal.Model.SIWEAuthenticateResponse) {}
                override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {}
                override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {}

                override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {
                    relayConnected = state.isAvailable
                    Log.d(TAG, "Connection state: ${state.isAvailable}")
                }

                override fun onError(error: Modal.Model.Error) {
                    val msg = error.throwable.message ?: ""
                    Log.e(TAG, "AppKit error: $msg")
                    if (msg.contains("403") || msg.contains("Invalid project")) {
                        Log.e(TAG, "Dashboard fix needed: add com.securecall.app.* to Allowed Application IDs at cloud.reown.com")
                    }
                }
            })

            isInitialized = true
            Log.d(TAG, "Reown AppKit initialized — isInitialized=true (project=$PROJECT_ID)")
        } catch (e: Throwable) {
            Log.e(TAG, "AppKit init failed (non-fatal): ${e.message}")
        }
    }

    /**
     * Start wallet connection via AppKit.
     * Creates a pairing URI and triggers the connection flow.
     */
    fun connect(context: Context, callback: (Boolean, String) -> Unit) {
        if (!isInitialized) {
            callback(false, "WalletConnect not initialized")
            return
        }

        try {
            connectCallback = callback

            val namespaces = mapOf(
                "eip155" to Modal.Model.Namespace.Proposal(
                    chains = listOf("eip155:1"),
                    methods = listOf("eth_sendTransaction", "personal_sign"),
                    events = listOf("chainChanged", "accountsChanged")
                )
            )

            val pairing = CoreClient.Pairing.create { error ->
                Log.e(TAG, "Pairing create error: ${error.throwable.message}")
            }

            if (pairing == null) {
                callback(false, "Failed to create pairing")
                return
            }

            val connectParams = Modal.Params.Connect(
                namespaces = namespaces,
                optionalNamespaces = null,
                properties = null,
                pairing = pairing
            )

            AppKit.connect(
                connect = connectParams,
                onSuccess = { uri: String ->
                    Log.d(TAG, "Connect request sent, opening wallet")
                    try {
                        val wcIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(pairing.uri)
                        )
                        wcIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(wcIntent)
                    } catch (e: Throwable) {
                        Log.w(TAG, "No wallet app found: ${e.message}")
                        callback(false, "no_wallet_app")
                    }
                },
                onError = { error: Modal.Model.Error ->
                    val msg = error.throwable.message ?: "unknown"
                    Log.e(TAG, "Connect error: $msg")
                    if (msg.contains("Timed out") || msg.contains("403") || msg.contains("Subscribe")) {
                        callback(false, "WalletConnect relay unavailable — known SDK issue.\n\nUse manual wallet entry instead.")
                    } else {
                        callback(false, "Connection failed: $msg")
                    }
                    connectCallback = null
                }
            )
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            Log.w(TAG, "AppKit connect failed: $msg")
            if (msg.contains("Timed out") || msg.contains("Subscribe") || msg.contains("Publish")) {
                callback(false, "WalletConnect relay timed out — known SDK issue (reown-kotlin #240).\n\nUse manual wallet entry instead (enter 0x address above).")
            } else {
                callback(false, "WalletConnect connection failed: $msg")
            }
            connectCallback = null
        }
    }

    fun getConnectedWallet(): String? {
        if (!isInitialized) return connectedAddress
        return try {
            AppKit.getAccount()?.address ?: connectedAddress
        } catch (e: Throwable) {
            connectedAddress
        }
    }

    fun disconnect(context: Context) {
        try {
            AppKit.disconnect(
                onSuccess = { connectedAddress = null; Log.d(TAG, "Disconnected") },
                onError = { error: Throwable -> Log.e(TAG, "Disconnect error: ${error.message}"); connectedAddress = null }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Disconnect failed: ${e.message}")
            connectedAddress = null
        }
    }

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
                    Log.d(TAG, "Verification: $amount IFR → $tier (permanent)")
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
        } catch (e: Throwable) {
            callback(false, "Verification failed — use manual wallet entry instead")
        }
    }
}

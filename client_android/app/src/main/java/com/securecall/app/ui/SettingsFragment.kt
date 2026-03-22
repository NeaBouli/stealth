package com.securecall.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.securecall.app.BuildConfig
import com.securecall.app.R
import com.securecall.app.config.FeatureProviderRegistry
import com.securecall.app.config.TierManager

class SettingsFragment : PreferenceFragmentCompat() {

    // Stealth-delete: 5-tap rapid trigger
    private var resetTapCount = 0
    private var resetFirstTapTime = 0L
    private val RESET_TAP_WINDOW = 5000L // 5 seconds
    private val RESET_TAP_TARGET = 5

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Tier display — use TierManager for effective tier
        val effectiveTier = TierManager.getCurrentTier(requireContext())
        findPreference<Preference>("pref_tier")?.summary = effectiveTier

        // Version
        findPreference<Preference>("pref_version")?.summary = BuildConfig.VERSION_NAME

        // Build number
        findPreference<Preference>("pref_build")?.summary = BuildConfig.VERSION_CODE.toString()

        // Dark mode
        findPreference<ListPreference>("pref_dark_mode")?.setOnPreferenceChangeListener { _, newValue ->
            when (newValue as String) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            true
        }

        // Security features (read-only display based on effective tier)
        val fp = try { FeatureProviderRegistry.get() } catch (_: Exception) { null }
        if (fp != null) {
            findPreference<Preference>("pref_cert_pinning")?.summary =
                if (fp.certificatePinning) getString(R.string.enabled) else getString(R.string.disabled)
            findPreference<Preference>("pref_device_attestation")?.summary =
                if (fp.deviceAttestationRequired) getString(R.string.enabled) else getString(R.string.disabled)
            findPreference<Preference>("pref_hardware_keystore")?.summary =
                if (fp.hardwareKeystoreRequired) getString(R.string.enabled) else getString(R.string.disabled)

            configureAntiRecordingSettings(fp)
        }

        // Background service toggle
        findPreference<SwitchPreferenceCompat>("pref_background_service")?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            val ws = com.securecall.app.net.WebSocketService.instance
            ws?.updateForegroundMode(enabled)
            true
        }

        // Upgrade button (only for FREE effective tier)
        findPreference<Preference>("pref_upgrade")?.apply {
            isVisible = TierManager.isFreeTier(requireContext())
            setOnPreferenceClickListener {
                try {
                    val intent = Intent()
                    intent.setClassName(requireContext().packageName, "com.securecall.app.billing.UpgradeActivity")
                    startActivity(intent)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(requireContext(), "Use an activation code below to upgrade", android.widget.Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        // Activation code section
        configureActivationCode(effectiveTier)

        // IFR Token Unlock section
        configureIfrUnlock(effectiveTier)

        // SecureCall ID (tap to copy) — read fresh from SharedPreferences each time
        val prefs = requireContext().getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
        findPreference<Preference>("pref_client_id")?.apply {
            summary = prefs.getString("client_id", "Not registered")
            setOnPreferenceClickListener {
                val ctx = requireContext()
                val freshId = ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("client_id", null)
                    ?: com.securecall.app.net.WebSocketService.instance?.getLocalClientId()
                    ?: "Not registered"
                // Show dialog with full ID so user can verify before copy
                android.app.AlertDialog.Builder(ctx)
                    .setTitle(getString(R.string.settings_client_id))
                    .setMessage(freshId)
                    .setPositiveButton("Copy") { _, _ ->
                        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SecureCall ID", freshId))
                        android.widget.Toast.makeText(ctx, "Copied: $freshId", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }

        // Phone number (manual entry for carriers that don't provide it via API)
        findPreference<EditTextPreference>("pref_phone_number")?.apply {
            val savedNumber = prefs.getString("manual_phone_number", null)
            if (!savedNumber.isNullOrBlank()) {
                summary = savedNumber
            }
            text = savedNumber
            setOnPreferenceChangeListener { _, newValue ->
                val number = (newValue as? String)?.trim()
                if (!number.isNullOrBlank()) {
                    prefs.edit().putString("manual_phone_number", number).apply()
                    summary = number
                } else {
                    prefs.edit().remove("manual_phone_number").apply()
                    summary = getString(R.string.pref_phone_number_summary)
                }
                // Re-register with server to update phone number
                com.securecall.app.net.WebSocketService.instance?.reRegister()
                true
            }
        }

        // eSIM + VPN (Premium only)
        configureAnonymousNetwork(effectiveTier)
        configureVpn(effectiveTier)

        // About section links
        setupAboutLinks()

        // Licenses placeholder
        findPreference<Preference>("pref_licenses")?.summary = "Apache 2.0, MIT, BSD"

        // Donation addresses — copy to clipboard on tap
        setupDonationPrefs()

        // Stealth-delete: 5-tap rapid trigger on "Reset App"
        findPreference<Preference>("pref_reset_app")?.setOnPreferenceClickListener {
            handleResetTap()
            true
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Add bottom padding so last preference item scrolls above bottom nav bar
        listView.clipToPadding = false
        listView.setPadding(listView.paddingLeft, listView.paddingTop, listView.paddingRight, 300)
    }

    override fun onResume() {
        super.onResume()
        // Refresh SecureCall ID summary in case it changed
        val prefs = requireContext().getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
        findPreference<Preference>("pref_client_id")?.summary = prefs.getString("client_id", "Not registered")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.securecall.app.vpn.VpnController.VPN_PERMISSION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                com.securecall.app.vpn.VpnController.start(requireContext())
            } else {
                // Permission denied — revert toggle
                com.securecall.app.vpn.VpnController.setEnabled(requireContext(), false)
                findPreference<SwitchPreferenceCompat>("pref_vpn_enabled")?.isChecked = false
                updateVpnStatus(requireContext(), true)
            }
        }
    }

    private fun setupAboutLinks() {
        findPreference<Preference>("pref_github")?.setOnPreferenceClickListener {
            openUrl("https://github.com/NeaBouli/stealth")
            true
        }
        findPreference<Preference>("pref_wiki")?.setOnPreferenceClickListener {
            openUrl("https://github.com/NeaBouli/stealth/wiki")
            true
        }
        findPreference<Preference>("pref_report_bug")?.setOnPreferenceClickListener {
            openUrl("https://github.com/NeaBouli/stealth/issues")
            true
        }
        findPreference<Preference>("pref_privacy")?.setOnPreferenceClickListener {
            openUrl("https://neabouli.github.io/stealth/privacy.html")
            true
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun setupDonationPrefs() {
        val addresses = mapOf(
            "pref_donate_eth" to "0xA0860f872a9cAB34817D9a764e71ab43B942b275",
            "pref_donate_btc" to "bc1qu0z0yur24cck25wc6rmack9tvczvx6g50y9sse",
            "pref_donate_sol" to "7tXfgsfw5SPsXMFQD1XYMSMYko77anuxNyRfY6YaHXDV"
        )
        val ctx = requireContext()
        for ((key, addr) in addresses) {
            findPreference<Preference>(key)?.apply {
                summary = addr
                setOnPreferenceClickListener {
                    val clip = android.content.ClipData.newPlainText("address", addr)
                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(clip)
                    android.widget.Toast.makeText(ctx, "Address copied", android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
        findPreference<Preference>("pref_donate_ifr")?.setOnPreferenceClickListener {
            openUrl("https://ifrunit.tech")
            true
        }
    }

    private fun configureActivationCode(effectiveTier: String) {
        val isUpgraded = effectiveTier != "FREE"
        val codePref = findPreference<EditTextPreference>("pref_activation_code")
        val activateButton = findPreference<Preference>("pref_activate_button")

        if (isUpgraded) {
            // Already Pro/Premium — hide input, show status, hide upgrade button
            codePref?.isVisible = false
            findPreference<Preference>("pref_upgrade")?.isVisible = false
            activateButton?.apply {
                title = getString(R.string.pref_plan_active, effectiveTier)
                isEnabled = false
                summary = null
            }
        } else {
            // Free tier — show activation code input
            codePref?.isVisible = true
            codePref?.setOnPreferenceChangeListener { _, _ -> true }

            activateButton?.apply {
                title = getString(R.string.pref_activate)
                isEnabled = true
                summary = getString(R.string.pref_activation_code_summary)
                setOnPreferenceClickListener {
                    val code = codePref?.text?.trim() ?: ""
                    if (code.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(), "Enter an activation code first", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnPreferenceClickListener true
                    }
                    submitActivationCode(code)
                    true
                }
            }
        }
    }

    private fun submitActivationCode(code: String) {
        val ctx = requireContext()
        val ws = com.securecall.app.net.WebSocketService.instance
        if (ws == null || !ws.isConnected) {
            android.widget.Toast.makeText(ctx, getString(R.string.activation_error_connection), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button during request
        findPreference<Preference>("pref_activate_button")?.apply {
            isEnabled = false
            summary = "Validating\u2026"
        }

        ws.activateCode(code) { success, tier, error ->
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (success && tier.isNotEmpty()) {
                    // Store activated tier
                    TierManager.setActivatedTier(ctx, tier)
                    android.widget.Toast.makeText(ctx, getString(R.string.activation_success, tier.uppercase()), android.widget.Toast.LENGTH_LONG).show()
                    // Restart app to apply new tier
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }, 1500)
                } else {
                    // Failed
                    val msg = when (error) {
                        "invalid" -> getString(R.string.activation_error_invalid)
                        "exhausted" -> getString(R.string.activation_error_exhausted)
                        "timeout", "not_connected" -> getString(R.string.activation_error_connection)
                        else -> "Activation failed: $error"
                    }
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                    findPreference<Preference>("pref_activate_button")?.apply {
                        isEnabled = true
                        summary = getString(R.string.pref_activation_code_summary)
                    }
                }
            }
        }
    }

    private fun configureAnonymousNetwork(effectiveTier: String) {
        val isPremium = TierManager.isPremium(requireContext())
        val ctx = requireContext()

        // FIX 2: Hide eSIM options if device doesn't support eSIM
        val hasEsim = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val euicc = ctx.getSystemService(android.telephony.euicc.EuiccManager::class.java)
            euicc?.isEnabled == true
        } else false

        findPreference<Preference>("pref_esim_setup")?.apply {
            if (!hasEsim) {
                isVisible = false
            } else if (!isPremium) {
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                isEnabled = true
                setOnPreferenceClickListener {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                        startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                            startActivity(intent)
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(ctx, "eSIM settings not available on this device", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
            }
        }

        findPreference<SwitchPreferenceCompat>("pref_esim_routing")?.apply {
            if (!hasEsim) {
                isVisible = false
            } else if (!isPremium) {
                isChecked = false
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                isChecked = com.securecall.app.net.NetworkManager.isEsimRoutingEnabled(ctx)
                isEnabled = true
                setOnPreferenceChangeListener { _, newValue ->
                    com.securecall.app.net.NetworkManager.setEsimRouting(ctx, newValue as Boolean)
                    true
                }
            }
        }

        findPreference<Preference>("pref_network_info")?.apply {
            summary = com.securecall.app.net.NetworkManager.getActiveNetworkInfo(ctx)
            if (com.securecall.app.net.NetworkManager.isBound()) {
                summary = summary.toString() + " (bound)"
            }
        }

        findPreference<ListPreference>("pref_network_transport")?.apply {
            if (!isPremium) {
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                isEnabled = true
                value = com.securecall.app.net.NetworkManager.getPreferredTransport(ctx)
                setOnPreferenceChangeListener { _, newValue ->
                    com.securecall.app.net.NetworkManager.setPreferredTransport(ctx, newValue as String)
                    if (com.securecall.app.net.NetworkManager.isEsimRoutingEnabled(ctx)) {
                        com.securecall.app.net.NetworkManager.bindToPreferredNetwork(ctx)
                    }
                    true
                }
            }
        }
    }

    private fun configureVpn(effectiveTier: String) {
        val isPremium = TierManager.isPremium(requireContext())
        val ctx = requireContext()

        findPreference<SwitchPreferenceCompat>("pref_vpn_enabled")?.apply {
            if (!isPremium) {
                isChecked = false
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                isChecked = com.securecall.app.vpn.VpnController.isEnabled(ctx)
                isEnabled = true
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    com.securecall.app.vpn.VpnController.setEnabled(ctx, enabled)
                    if (enabled) {
                        if (!com.securecall.app.vpn.VpnController.hasConfig(ctx)) {
                            android.widget.Toast.makeText(ctx, "Configure WireGuard first", android.widget.Toast.LENGTH_SHORT).show()
                            return@setOnPreferenceChangeListener false
                        }
                        val vpnIntent = android.net.VpnService.prepare(ctx)
                        if (vpnIntent != null) {
                            @Suppress("DEPRECATION")
                            startActivityForResult(vpnIntent, com.securecall.app.vpn.VpnController.VPN_PERMISSION_REQUEST)
                        } else {
                            com.securecall.app.vpn.VpnController.start(ctx)
                        }
                    } else {
                        com.securecall.app.vpn.VpnController.stop(ctx)
                    }
                    // Update status text immediately
                    updateVpnStatus(ctx, true)
                    true
                }
            }
        }

        updateVpnStatus(ctx, isPremium)

        findPreference<Preference>("pref_vpn_config")?.apply {
            if (!isPremium) {
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                isEnabled = true
                if (com.securecall.app.vpn.VpnController.hasConfig(ctx)) {
                    val prefs = ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
                    summary = prefs.getString("vpn_server_endpoint", "") + ":" + prefs.getInt("vpn_server_port", 51820)
                }
                setOnPreferenceClickListener {
                    showVpnConfigDialog()
                    true
                }
            }
        }

        findPreference<SwitchPreferenceCompat>("pref_vpn_kill_switch")?.apply {
            if (!isPremium) {
                isChecked = false
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                isEnabled = true
                isChecked = ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
                    .getBoolean("vpn_kill_switch", false)
                setOnPreferenceChangeListener { _, newValue ->
                    ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("vpn_kill_switch", newValue as Boolean).apply()
                    true
                }
            }
        }
    }

    private fun updateVpnStatus(ctx: android.content.Context, isPremium: Boolean) {
        findPreference<Preference>("pref_vpn_status")?.summary = when {
            !isPremium -> getString(R.string.pref_premium_feature)
            com.securecall.app.vpn.GhostVpnService.isActive ->
                "Connected: ${com.securecall.app.vpn.GhostVpnService.connectedServer ?: "Unknown"}"
            com.securecall.app.vpn.VpnController.isEnabled(ctx) && com.securecall.app.vpn.VpnController.hasConfig(ctx) ->
                "Enabled — waiting for connection"
            com.securecall.app.vpn.VpnController.isEnabled(ctx) ->
                "Enabled — no configuration"
            else -> "VPN disabled"
        }
    }

    private fun showVpnConfigDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)

        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = resources.getDimensionPixelSize(R.dimen.spacing_lg)
            setPadding(pad, pad, pad, 0)
        }

        val endpointInput = android.widget.EditText(ctx).apply {
            hint = "Server endpoint (IP or hostname)"
            setText(prefs.getString("vpn_server_endpoint", ""))
        }
        val portInput = android.widget.EditText(ctx).apply {
            hint = "Port (default: 51820)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("vpn_server_port", 51820).toString())
        }
        val pubKeyInput = android.widget.EditText(ctx).apply {
            hint = "Server public key"
            setText(prefs.getString("vpn_server_public_key", ""))
        }
        val privKeyInput = android.widget.EditText(ctx).apply {
            hint = "Client private key"
            setText(prefs.getString("vpn_client_private_key", ""))
        }
        val dnsInput = android.widget.EditText(ctx).apply {
            hint = "DNS (default: 1.1.1.1)"
            setText(prefs.getString("vpn_dns", "1.1.1.1"))
        }
        val clientAddrInput = android.widget.EditText(ctx).apply {
            hint = "Client address (e.g. 10.99.0.2/31)"
            setText(prefs.getString("vpn_client_address", ""))
        }

        layout.addView(endpointInput)
        layout.addView(portInput)
        layout.addView(pubKeyInput)
        layout.addView(privKeyInput)
        layout.addView(dnsInput)
        layout.addView(clientAddrInput)

        android.app.AlertDialog.Builder(ctx)
            .setTitle("WireGuard Configuration")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val endpoint = endpointInput.text.toString().trim()
                val port = portInput.text.toString().toIntOrNull() ?: 51820
                val pubKey = pubKeyInput.text.toString().trim()
                val privKey = privKeyInput.text.toString().trim()
                val dns = dnsInput.text.toString().trim().ifEmpty { "1.1.1.1" }

                if (endpoint.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "Server endpoint required", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val clientAddr = clientAddrInput.text.toString().trim().ifEmpty { "10.66.66.2/32" }
                com.securecall.app.vpn.VpnController.saveConfig(ctx, endpoint, port, pubKey, privKey, dns, "0.0.0.0/0",
                    clientAddr, prefs.getBoolean("vpn_kill_switch", false))
                findPreference<Preference>("pref_vpn_config")?.summary = "$endpoint:$port"
                android.widget.Toast.makeText(ctx, "VPN config saved", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton("Clear") { _, _ ->
                com.securecall.app.vpn.VpnController.clearConfig(ctx)
                findPreference<Preference>("pref_vpn_config")?.summary = getString(R.string.pref_vpn_config_summary)
                android.widget.Toast.makeText(ctx, "VPN config cleared", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun configureIfrUnlock(effectiveTier: String) {
        val ctx = requireContext()
        val wallet = com.securecall.app.config.IfrLockManager.getWalletAddress(ctx)
        val ifrTier = com.securecall.app.config.IfrLockManager.getIfrTier(ctx)
        val amount = com.securecall.app.config.IfrLockManager.getLockedAmount(ctx)
        val method = com.securecall.app.config.IfrLockManager.getVerificationMethod(ctx)
        val daysRemaining = com.securecall.app.config.IfrLockManager.getDaysRemaining(ctx)

        // Status display with expiration info and token count
        findPreference<Preference>("pref_ifr_status")?.summary = when {
            ifrTier != null && method == com.securecall.app.config.IfrLockManager.METHOD_WALLETCONNECT ->
                getString(R.string.ifr_status_active, amount, ifrTier.uppercase()) + " (lifetime)"
            ifrTier != null && daysRemaining > 0 ->
                getString(R.string.ifr_status_active, amount, ifrTier.uppercase()) + " (expires in $daysRemaining days)"
            ifrTier != null && daysRemaining == 0 ->
                getString(R.string.ifr_status_active, amount, ifrTier.uppercase()) + " (expiring today!)"
            wallet != null && amount != "0" ->
                "Wallet: ${wallet.take(6)}...${wallet.takeLast(4)} — $amount IFR held"
            wallet != null ->
                "Wallet: ${wallet.take(6)}...${wallet.takeLast(4)}"
            else -> getString(R.string.ifr_status_none) + "\n" + getString(R.string.ifr_threshold_info)
        }

        // Wallet input
        findPreference<EditTextPreference>("pref_ifr_wallet")?.apply {
            text = wallet
            if (wallet != null) summary = "${wallet.take(6)}...${wallet.takeLast(4)}"
            setOnPreferenceChangeListener { _, newValue ->
                val addr = (newValue as? String)?.trim() ?: ""
                if (addr.matches(Regex("^0x[0-9a-fA-F]{40}$"))) {
                    ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("ifr_wallet_address", addr.lowercase()).apply()
                    summary = "${addr.take(6)}...${addr.takeLast(4)}"
                } else if (addr.isNotEmpty()) {
                    android.widget.Toast.makeText(ctx, "Invalid wallet address", android.widget.Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        // Verify button
        findPreference<Preference>("pref_ifr_verify")?.apply {
            summary = getString(R.string.ifr_threshold_info)
            setOnPreferenceClickListener {
                val addr = findPreference<EditTextPreference>("pref_ifr_wallet")?.text?.trim() ?: ""
                if (addr.isEmpty() || !addr.matches(Regex("^0x[0-9a-fA-F]{40}$"))) {
                    android.widget.Toast.makeText(ctx, "Enter a valid wallet address first", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnPreferenceClickListener true
                }
                submitIfrVerification(addr)
                true
            }
        }

        // WalletConnect — connect wallet for permanent tier unlock
        findPreference<Preference>("pref_ifr_walletconnect")?.apply {
            val wcWallet = com.securecall.app.wallet.WalletConnectManager.getConnectedWallet()
            isEnabled = true
            summary = when {
                method == com.securecall.app.config.IfrLockManager.METHOD_WALLETCONNECT && ifrTier != null ->
                    "Connected: ${wallet?.take(6)}...${wallet?.takeLast(4)} — Permanent unlock active"
                wcWallet != null ->
                    "Connected: ${wcWallet.take(6)}...${wcWallet.takeLast(4)} — Tap to verify IFR"
                else ->
                    getString(R.string.pref_ifr_walletconnect_summary)
            }
            title = if (method == com.securecall.app.config.IfrLockManager.METHOD_WALLETCONNECT) {
                "Disconnect WalletConnect"
            } else {
                getString(R.string.pref_ifr_walletconnect)
            }
            setOnPreferenceClickListener {
                if (method == com.securecall.app.config.IfrLockManager.METHOD_WALLETCONNECT) {
                    // Disconnect
                    com.securecall.app.wallet.WalletConnectManager.disconnect(ctx)
                    com.securecall.app.config.IfrLockManager.clearIfrUnlock(ctx)
                    android.widget.Toast.makeText(ctx, "Wallet disconnected", android.widget.Toast.LENGTH_SHORT).show()
                    configureIfrUnlock(effectiveTier)
                } else if (!com.securecall.app.wallet.WalletConnectManager.isInitialized) {
                    android.widget.Toast.makeText(ctx, "WalletConnect initializing...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // Start WalletConnect pairing
                    summary = "Connecting..."
                    isEnabled = false
                    com.securecall.app.wallet.WalletConnectManager.connect(ctx) { success, result ->
                        activity?.runOnUiThread {
                            if (!isAdded) return@runOnUiThread
                            isEnabled = true
                            if (success) {
                                // Wallet connected — now verify IFR balance
                                summary = "Connected: ${result.take(6)}...${result.takeLast(4)} — Verifying..."
                                com.securecall.app.wallet.WalletConnectManager.verifyAndUnlock(ctx, result) { verified, msg ->
                                    activity?.runOnUiThread {
                                        if (!isAdded) return@runOnUiThread
                                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                                        if (verified) {
                                            configureIfrUnlock(effectiveTier)
                                        } else {
                                            summary = msg
                                        }
                                    }
                                }
                            } else if (result == "no_wallet_app") {
                                summary = "No wallet app found — install MetaMask or Trust Wallet"
                            } else {
                                summary = result
                            }
                        }
                    }
                }
                true
            }
        }
    }

    private fun submitIfrVerification(walletAddress: String) {
        val ctx = requireContext()
        findPreference<Preference>("pref_ifr_verify")?.apply {
            isEnabled = false
            summary = "Verifying on Ethereum..."
        }

        com.securecall.app.config.IfrLockManager.verify(ctx, walletAddress) { success, tier, amount, error ->
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                findPreference<Preference>("pref_ifr_verify")?.isEnabled = true
                if (success && tier.isNotEmpty()) {
                    findPreference<Preference>("pref_ifr_status")?.summary =
                        getString(R.string.ifr_status_active, amount, tier.uppercase())
                    findPreference<Preference>("pref_ifr_verify")?.summary =
                        getString(R.string.ifr_verify_success, amount, tier.uppercase())
                    android.widget.Toast.makeText(ctx,
                        getString(R.string.ifr_verify_success, amount, tier.uppercase()),
                        android.widget.Toast.LENGTH_LONG).show()
                    // Restart to apply tier
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }, 2000)
                } else {
                    val amountInfo = if (amount != "0" && amount.isNotEmpty()) " ($amount IFR held)" else ""
                    val msg = when (error) {
                        "insufficient" -> getString(R.string.ifr_verify_insufficient) + amountInfo
                        "wallet_bound" -> getString(R.string.ifr_wallet_bound)
                        "invalid_address" -> "Invalid wallet address format"
                        "not_connected", "timeout" -> getString(R.string.activation_error_connection)
                        "all_rpc_failed" -> "Ethereum RPC unavailable — try again later"
                        else -> getString(R.string.ifr_verify_error, error) + amountInfo
                    }
                    findPreference<Preference>("pref_ifr_verify")?.summary = msg
                    // Update status to show the balance even on failure
                    if (amount != "0" && amount.isNotEmpty()) {
                        val addr = findPreference<androidx.preference.EditTextPreference>("pref_ifr_wallet")?.text?.trim() ?: ""
                        if (addr.isNotEmpty()) {
                            findPreference<Preference>("pref_ifr_status")?.summary =
                                "Wallet: ${addr.take(6)}...${addr.takeLast(4)} — $amount IFR held"
                        }
                    }
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun handleResetTap() {
        val now = System.currentTimeMillis()
        if (now - resetFirstTapTime > RESET_TAP_WINDOW) {
            resetTapCount = 0
            resetFirstTapTime = now
        }
        resetTapCount++
        android.util.Log.d("STEALTH_DELETE", "Reset tap #$resetTapCount (window=${now - resetFirstTapTime}ms)")

        val ctx = context ?: return
        try {
            val vibrator = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            when (resetTapCount) {
                3 -> vibrator?.vibrate(50)
                4 -> vibrator?.vibrate(longArrayOf(0, 40, 60, 40), -1)
                RESET_TAP_TARGET -> {
                    vibrator?.vibrate(150)
                    showStealthDeleteConfirmation()
                    resetTapCount = 0
                    return
                }
            }
        } catch (_: Exception) {}
    }

    private fun showStealthDeleteConfirmation() {
        val ctx = context ?: return
        android.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.stealth_delete_title))
            .setMessage(getString(R.string.stealth_delete_message))
            .setPositiveButton(getString(R.string.stealth_delete_confirm)) { _, _ ->
                com.securecall.app.security.StealthDeleteManager.execute(ctx)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            // Make the delete button red
            .getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(android.graphics.Color.RED)
    }

    /**
     * Configure anti-recording protection settings based on tier.
     *
     * - FREE:    All toggles locked OFF, shows "PRO feature" label
     * - PRO:     All toggles available, defaults ON
     * - PREMIUM: All forced ON, toggles disabled (not user-changeable)
     */
    private fun configureAntiRecordingSettings(fp: com.securecall.app.config.FeatureProvider) {
        val isPremium = fp.tier == "PREMIUM"
        val isPro = fp.tier == "PRO"
        val isFree = fp.tier == "FREE"

        // Block Screenshots toggle — available on all tiers
        findPreference<SwitchPreferenceCompat>("pref_block_screenshots")?.apply {
            if (isPremium) {
                isChecked = true
                isEnabled = false
                summary = getString(R.string.pref_block_screenshots_premium)
            } else {
                isEnabled = true
                if (!preferenceManager.sharedPreferences!!.contains("pref_block_screenshots")) {
                    // Default: ON for Pro, OFF for Free
                    isChecked = isPro
                }
            }
        }

        // Exclusive Microphone toggle
        findPreference<SwitchPreferenceCompat>("pref_exclusive_mic")?.apply {
            if (isPremium) {
                isChecked = true
                isEnabled = false
                summary = getString(R.string.always_on)
            } else if (isFree) {
                isChecked = false
                isEnabled = false
                summary = getString(R.string.pref_pro_feature)
            } else {
                isEnabled = true
                if (!preferenceManager.sharedPreferences!!.contains("pref_exclusive_mic")) {
                    isChecked = true
                }
            }
        }

        // Detect Screen Recording toggle
        findPreference<SwitchPreferenceCompat>("pref_detect_recording")?.apply {
            if (isPremium) {
                isChecked = true
                isEnabled = false
                summary = getString(R.string.always_on)
            } else if (isFree) {
                isChecked = false
                isEnabled = false
                summary = getString(R.string.pref_pro_feature)
            } else {
                isEnabled = true
                if (!preferenceManager.sharedPreferences!!.contains("pref_detect_recording")) {
                    isChecked = true
                }
            }
        }

        // Security Level display
        findPreference<Preference>("pref_security_level")?.summary = when (fp.tier) {
            "PREMIUM" -> "Maximum — all protections enforced"
            "PRO" -> "High — critical threats blocked"
            else -> "Basic — warnings only"
        }
    }
}

package com.securecall.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.securecall.app.BuildConfig
import com.securecall.app.R
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.securecall.app.config.FeatureProviderRegistry
import com.securecall.app.config.TierManager

class SettingsFragment : PreferenceFragmentCompat() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        com.securecall.app.vpn.VpnFeature.onPermissionResult(this, result.resultCode)
    }

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
        val bgServicePref = findPreference<SwitchPreferenceCompat>("pref_background_service")
        if (!com.securecall.app.net.ForegroundServicePolicy.allowsPersistentIdleSignaling(android.os.Build.VERSION.SDK_INT)) {
            // Android 15+ (API 35): persistent background signaling is not allowed.
            // The toggle is shown unchecked + disabled; incoming calls are managed
            // by secure push notifications (FCM).
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putBoolean("pref_background_service", false)
                .apply()
            bgServicePref?.apply {
                isChecked = false
                isEnabled = false
                summary = getString(R.string.pref_background_service_summary_push)
            }
        } else {
            bgServicePref?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putBoolean("pref_background_service", enabled)
                    .apply()
                val ws = com.securecall.app.net.WebSocketService.instance
                android.util.Log.w("SettingsFragment", "Background service toggle: enabled=$enabled, ws=${if (ws != null) "OK" else "NULL"}")
                if (ws != null) {
                    ws.updateForegroundMode(enabled)
                } else if (enabled) {
                    val serviceIntent = Intent(requireContext(), com.securecall.app.net.WebSocketService::class.java)
                    androidx.core.content.ContextCompat.startForegroundService(requireContext(), serviceIntent)
                } else {
                    android.util.Log.d("SettingsFragment", "WebSocketService already stopped")
                }
                true
            }
        }

        // Battery optimization status + toggle
        configureBatteryOptimization()

        // Upgrade button — hidden for Premium, shows "Upgrade to Premium" for Pro
        findPreference<Preference>("pref_upgrade")?.apply {
            when (effectiveTier) {
                "PREMIUM" -> isVisible = false
                "PRO" -> { isVisible = true; title = "Upgrade to Premium" }
                else -> { isVisible = true; title = "Buy Pro / Premium" }
            }
            summary = "Open checkout and activation-code options"
            setOnPreferenceClickListener {
                openUrl("https://stealthx.tech/#pricing")
                true
            }
        }

        // Custom Call ID section (Premium only)
        configureCustomCallId(effectiveTier)

        // Activation code section
        configureActivationCode(effectiveTier)

        // SecureCall ID (tap to copy) — show custom ID if set, otherwise random ID
        val prefs = requireContext().getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)

        findPreference<Preference>("pref_client_id")?.apply {
            val customId = prefs.getString("custom_call_id", null)
            val clientId = prefs.getString("client_id", "Not registered")
            summary = if (!customId.isNullOrEmpty()) "$customId ($clientId)" else clientId
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

        // eSIM setup and preferred network transport
        configureAnonymousNetwork(effectiveTier)

        // Flavor boundary: no-op in Free/Pro, real WireGuard controls only in
        // the direct-download Premium APK source set.
        com.securecall.app.vpn.VpnFeature.configure(this, vpnPermissionLauncher)

        // About section links
        setupAboutLinks()
        setupCrashReportPreference()
        setupAdPrivacyOptions()

        // Licenses & Disclaimer
        findPreference<Preference>("pref_licenses")?.apply {
            summary = "Disclaimer, Licenses & Legal — tap to view"
            isSelectable = true
        }

        // Donation addresses — copy to clipboard on tap
        setupDonationPrefs()

        // SecLog Diagnostics (Pro/Premium only)
        setupSecLog(effectiveTier)

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
        val customId = prefs.getString("custom_call_id", null)
        val clientId = prefs.getString("client_id", "Not registered")
        findPreference<Preference>("pref_client_id")?.summary =
            if (!customId.isNullOrEmpty()) "$customId ($clientId)" else clientId

        // BUG-022: Refresh network info + bound status on every resume
        refreshNetworkStatus()
        com.securecall.app.vpn.VpnFeature.refresh(this)
        configureBatteryOptimization()
        setupAdPrivacyOptions()
    }

    /** BUG-022: Refresh network info so eSIM status doesn't stay stale. */
    private fun refreshNetworkStatus() {
        val ctx = context ?: return
        findPreference<Preference>("pref_network_info")?.apply {
            val info = com.securecall.app.net.NetworkManager.getActiveNetworkInfo(ctx)
            summary = if (com.securecall.app.net.NetworkManager.isBound()) {
                "$info (bound)"
            } else {
                info
            }
        }
    }

    private fun setupAboutLinks() {
        findPreference<Preference>("pref_check_update")?.apply {
            summary = com.securecall.app.update.UpdateManager.getUpdateLabel()
            setOnPreferenceClickListener {
                val act = activity
                if (act != null) {
                    com.securecall.app.update.UpdateManager.checkAndPromptUpdate(act)
                } else {
                    com.securecall.app.update.UpdateManager.openUpdate(requireContext())
                }
                true
            }
        }
        findPreference<Preference>("pref_github")?.setOnPreferenceClickListener {
            openUrl("https://github.com/NeaBouli/stealth")
            true
        }
        findPreference<Preference>("pref_wiki")?.setOnPreferenceClickListener {
            openUrl("https://github.com/NeaBouli/stealth/wiki")
            true
        }
        findPreference<Preference>("pref_user_manual")?.setOnPreferenceClickListener {
            openUrl("https://stealthx.tech/wiki/user-manual.html")
            true
        }
        findPreference<Preference>("pref_report_bug")?.setOnPreferenceClickListener {
            openUrl("https://stealthx.tech/wiki/bug-report.html")
            true
        }
        findPreference<Preference>("pref_privacy")?.setOnPreferenceClickListener {
            openUrl("https://stealthx.tech/privacy.html")
            true
        }
        findPreference<Preference>("pref_terms")?.setOnPreferenceClickListener {
            openUrl("https://stealthx.tech/terms.html")
            true
        }
        findPreference<Preference>("pref_licenses")?.setOnPreferenceClickListener {
            openUrl("https://stealthx.tech/disclaimer.html")
            true
        }
    }

    private fun setupAdPrivacyOptions() {
        findPreference<Preference>("pref_ad_privacy")?.apply {
            isVisible = BuildConfig.TIER == "FREE" &&
                com.securecall.app.ads.AdMobManager.isPrivacyOptionsRequired(requireContext())
            setOnPreferenceClickListener {
                activity?.let { com.securecall.app.ads.AdMobManager.showPrivacyOptions(it) }
                true
            }
        }
    }

    private fun setupCrashReportPreference() {
        findPreference<SwitchPreferenceCompat>("pref_crash_reports")?.apply {
            isVisible = BuildConfig.TIER == "FREE"
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                try {
                    val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                    crashlytics.setCrashlyticsCollectionEnabled(enabled)
                    if (!enabled) crashlytics.deleteUnsentReports()
                } catch (e: Exception) {
                    android.util.Log.w("SettingsFragment", "Crash reporting preference update failed", e)
                }
                true
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "No browser available", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** SecLog diagnostics — Pro/Premium only. */
    private fun setupSecLog(effectiveTier: String) {
        val isFree = effectiveTier == "FREE"
        val ctx = requireContext()

        findPreference<SwitchPreferenceCompat>("pref_seclog_enabled")?.apply {
            if (isFree) {
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
                isChecked = false
            } else {
                isEnabled = true
                isChecked = com.securecall.app.debug.SecLogManager.isEnabled(ctx)
                setOnPreferenceChangeListener { _, newValue ->
                    com.securecall.app.debug.SecLogManager.setEnabled(ctx, newValue as Boolean)
                    updateSecLogSummary()
                    true
                }
            }
        }
        findPreference<Preference>("pref_seclog_export")?.apply {
            if (isFree) {
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                setOnPreferenceClickListener {
                    com.securecall.app.debug.SecLogManager.exportCsv(ctx)
                    true
                }
            }
        }
        findPreference<Preference>("pref_seclog_clear")?.apply {
            if (isFree) {
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                setOnPreferenceClickListener {
                    com.securecall.app.debug.SecLogManager.clearLogs()
                    android.widget.Toast.makeText(ctx, "Logs cleared", android.widget.Toast.LENGTH_SHORT).show()
                    updateSecLogSummary()
                    true
                }
            }
        }
        updateSecLogSummary()
    }

    private fun updateSecLogSummary() {
        val count = com.securecall.app.debug.SecLogManager.getEntryCount()
        findPreference<Preference>("pref_seclog_export")?.let {
            if (it.isEnabled) it.summary = "$count entries"
        }
    }

    private fun setupDonationPrefs() {
        val addresses = mapOf(
            "pref_donate_eth" to "0xA0860f872a9cAB34817D9a764e71ab43B942b275",
            "pref_donate_btc" to "bc1qu0z0yur24cck25wc6rmack9tvczvx6g50y9sse",
            "pref_donate_sol" to "7tXfgsfw5SPsXMFQD1XYMSMYko77anuxNyRfY6YaHXDV"
        )
        val ctx = requireContext()

        // Donation addresses — copy to clipboard on tap (collapse handled by initialExpandedChildrenCount)
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
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun configureBatteryOptimization() {
        val pref = findPreference<Preference>("pref_battery_optimization") ?: return
        if (!com.securecall.app.net.ForegroundServicePolicy.shouldRequestBatteryOptimizationExemption(
                android.os.Build.VERSION.SDK_INT
            )
        ) {
            pref.summary = getString(R.string.pref_battery_optimization_summary_push)
            pref.isEnabled = false
            return
        }

        val ctx = context ?: return
        val pm = ctx.getSystemService(android.os.PowerManager::class.java) ?: return
        val isIgnoring = pm.isIgnoringBatteryOptimizations(ctx.packageName)

        if (isIgnoring) {
            pref.summary = "✅ Unrestricted — connection stays alive"
        } else {
            pref.summary = "⚠️ Restricted — may lose connection in background"
        }

        pref.setOnPreferenceClickListener {
            if (isIgnoring) {
                android.widget.Toast.makeText(ctx, "Already unrestricted", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${ctx.packageName}")
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: open app details
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.parse("package:${ctx.packageName}")
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
            true
        }
    }

    private fun configureCustomCallId(effectiveTier: String) {
        val isPremium = effectiveTier == "PREMIUM"
        val category = findPreference<com.securecall.app.ui.CollapsiblePreferenceCategory>("pref_category_custom_id")
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)

        // Only visible for Premium users
        category?.isVisible = isPremium
        if (!isPremium) return

        // Show current custom ID
        val currentId = prefs.getString("custom_call_id", null)
        findPreference<Preference>("pref_custom_id_status")?.summary =
            if (currentId.isNullOrEmpty()) "Not set" else currentId

        // Activate button
        findPreference<Preference>("pref_custom_id_activate")?.setOnPreferenceClickListener {
            val id = findPreference<EditTextPreference>("pref_custom_id_input")?.text?.trim()?.lowercase() ?: ""
            val password = findPreference<EditTextPreference>("pref_custom_id_password")?.text ?: ""
            if (id.length < 3) {
                android.widget.Toast.makeText(ctx, "ID must be at least 3 characters", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnPreferenceClickListener true
            }
            if (password.length < 8) {
                android.widget.Toast.makeText(ctx, "Password must be at least 8 characters", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnPreferenceClickListener true
            }
            val deviceId = prefs.getString("client_id", "") ?: ""
            if (deviceId.isEmpty()) {
                android.widget.Toast.makeText(ctx, "Device not registered yet", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnPreferenceClickListener true
            }
            showCustomIdConfirmation(id, password, deviceId, false)
            true
        }

        // Transfer button
        findPreference<Preference>("pref_custom_id_transfer")?.setOnPreferenceClickListener {
            val id = findPreference<EditTextPreference>("pref_custom_id_input")?.text?.trim()?.lowercase() ?: ""
            val password = findPreference<EditTextPreference>("pref_custom_id_password")?.text ?: ""
            if (id.isEmpty() || password.length < 8) {
                android.widget.Toast.makeText(ctx, "Enter your existing ID and password", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnPreferenceClickListener true
            }
            val deviceId = prefs.getString("client_id", "") ?: ""
            showCustomIdConfirmation(id, password, deviceId, true)
            true
        }

        // Buy button → open website
        findPreference<Preference>("pref_custom_id_buy")?.setOnPreferenceClickListener {
            openUrl("https://stealthx.tech/wiki/custom-id.html")
            true
        }
    }

    private fun showCustomIdConfirmation(id: String, password: String, deviceId: String, isTransfer: Boolean) {
        val ctx = requireContext()
        val checkBox = android.widget.CheckBox(ctx).apply {
            text = "I understand \u2014 I have saved my password"
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(0, 16, 0, 0)
        }
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val dp = (24 * resources.displayMetrics.density).toInt()
            setPadding(dp, dp / 2, dp, 0)
            addView(android.widget.TextView(ctx).apply {
                text = "\u26A0\uFE0F Important: Save your password securely.\n\n" +
                    "If you lose your password, your Custom ID cannot be recovered. " +
                    "No replacement or refund will be issued.\n\n" +
                    "Your ID and password are stored as one-way cryptographic hashes. " +
                    "Not even we can see or recover them."
                setTextColor(0xFFDDDDDD.toInt())
                textSize = 14f
            })
            addView(checkBox)
        }
        val dialog = android.app.AlertDialog.Builder(ctx, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("Custom ID \u2014 No Recovery Possible")
            .setView(layout)
            .setPositiveButton("Confirm") { _, _ ->
                submitCustomId(id, password, deviceId, isTransfer)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = isChecked
        }
    }

    private fun submitCustomId(id: String, password: String, deviceId: String, isTransfer: Boolean) {
        val ctx = requireContext()
        val activateBtn = findPreference<Preference>("pref_custom_id_activate")
        val transferBtn = findPreference<Preference>("pref_custom_id_transfer")
        activateBtn?.isEnabled = false
        transferBtn?.isEnabled = false
        activateBtn?.summary = "Processing\u2026"

        Thread {
            try {
                val serverUrl = BuildConfig.SIGNAL_WS_URL
                    .replace("wss://", "https://").replace("ws://", "http://").replace("/signal", "")
                val json = org.json.JSONObject().apply {
                    put("id", id)
                    put("password", password)
                    put("deviceId", deviceId)
                }.toString()
                val mediaType = "application/json".toMediaTypeOrNull()
                val body = json.toRequestBody(mediaType)
                val request = okhttp3.Request.Builder()
                    .url("$serverUrl/custom-id/activate")
                    .post(body).build()
                val response = com.securecall.app.net.NetworkManager.buildPinnedClient().newCall(request).execute()
                val respBody = response.body?.string() ?: ""

                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    activateBtn?.isEnabled = true
                    transferBtn?.isEnabled = true
                    activateBtn?.summary = "Register this ID on your device"

                    if (response.isSuccessful && respBody.contains("\"success\":true")) {
                        val prefs = ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putString("custom_call_id", id).apply()
                        findPreference<Preference>("pref_custom_id_status")?.summary = id
                        val msg = if (isTransfer) "ID \"$id\" transferred to this device!" else "Custom ID \"$id\" activated!"
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        val error = when {
                            respBody.contains("wrong_password") -> "Wrong password"
                            respBody.contains("invalid_format") -> "Invalid ID format"
                            respBody.contains("reserved") -> "This ID is reserved"
                            respBody.contains("password_too_short") -> "Password too short (min 8)"
                            else -> "Failed: $respBody"
                        }
                        android.widget.Toast.makeText(ctx, error, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    activateBtn?.isEnabled = true
                    transferBtn?.isEnabled = true
                    activateBtn?.summary = "Register this ID on your device"
                    android.widget.Toast.makeText(ctx, "Connection error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun configureActivationCode(effectiveTier: String) {
        val isUpgraded = effectiveTier != "FREE"
        val codePref = findPreference<EditTextPreference>("pref_activation_code")
        val activateButton = findPreference<Preference>("pref_activate_button")

        if (isUpgraded) {
            // Already Pro/Premium — hide input, show status
            codePref?.isVisible = false
            // Only hide upgrade button for Premium (Pro can still upgrade to Premium)
            if (effectiveTier == "PREMIUM") {
                findPreference<Preference>("pref_upgrade")?.isVisible = false
            }
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
                        "max_devices" -> "Code already activated on maximum devices"
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
        val isProOrPremium = effectiveTier == "PRO" || effectiveTier == "PREMIUM"
        val ctx = requireContext()

        // Check eSIM hardware capability
        val hasEsim = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val euicc = ctx.getSystemService(android.telephony.euicc.EuiccManager::class.java)
            euicc?.isEnabled == true
        } else false

        findPreference<Preference>("pref_esim_setup")?.apply {
            when {
                !hasEsim -> {
                    isEnabled = false
                    summary = "eSIM not supported on this device"
                }
                !isProOrPremium -> {
                    isEnabled = false
                    summary = getString(R.string.pref_premium_feature)
                }
                else -> {
                    isEnabled = true
                    summary = getString(R.string.pref_esim_setup_summary)
                    setOnPreferenceClickListener {
                        try {
                            startActivity(android.content.Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
                        } catch (_: Exception) {
                            try {
                                startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(ctx, "eSIM settings not available", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        true
                    }
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
            if (!isProOrPremium) {
                isEnabled = false
                summary = getString(R.string.pref_premium_feature)
            } else {
                isEnabled = true
                value = com.securecall.app.net.NetworkManager.getPreferredTransport(ctx)
                summary = "Effective when switching networks (e.g. WiFi off \u2192 Mobile)"
                setOnPreferenceChangeListener { pref, newValue ->
                    val transport = newValue as String
                    com.securecall.app.net.NetworkManager.setPreferredTransport(ctx, transport)
                    com.securecall.app.net.NetworkManager.bindToPreferredNetwork(ctx)
                    pref.summary = "Effective when switching networks (e.g. WiFi off \u2192 Mobile)"
                    findPreference<Preference>("pref_network_info")?.apply {
                        val info = com.securecall.app.net.NetworkManager.getActiveNetworkInfo(ctx)
                        summary = if (com.securecall.app.net.NetworkManager.isBound()) "$info (bound)" else info
                    }
                    true
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
        android.util.Log.d("EMERGENCY_DELETE", "Tap #$resetTapCount (window=${now - resetFirstTapTime}ms)")

        val ctx = context ?: return
        try {
            val vibrator = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            when (resetTapCount) {
                3 -> vibrator?.vibrate(50)
                4 -> vibrator?.vibrate(longArrayOf(0, 40, 60, 40), -1)
                RESET_TAP_TARGET -> {
                    vibrator?.vibrate(150)
                    // Instant wipe — no confirmation dialog (TB-036)
                    android.util.Log.w("EMERGENCY_DELETE", "5-tap reached — executing immediate wipe")
                    com.securecall.app.security.StealthDeleteManager.execute(ctx)
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
        val isFree = fp.tier == "FREE"

        // Block Screenshots toggle
        findPreference<SwitchPreferenceCompat>("pref_block_screenshots")?.apply {
            if (isPremium) {
                isChecked = true
                isEnabled = false
                summary = getString(R.string.pref_block_screenshots_premium)
            } else if (isFree) {
                isChecked = false
                isEnabled = false
                summary = getString(R.string.pref_pro_feature)
            } else {
                // Pro: toggle available, default ON
                isEnabled = true
                if (!preferenceManager.sharedPreferences!!.contains("pref_block_screenshots")) {
                    isChecked = true
                }
                setOnPreferenceChangeListener { _, _ ->
                    // Immediately re-apply FLAG_SECURE after preference write
                    activity?.let {
                        it.window?.decorView?.post {
                            com.securecall.app.security.WindowSecurityHelper.applyFlagSecure(it)
                        }
                    }
                    true
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

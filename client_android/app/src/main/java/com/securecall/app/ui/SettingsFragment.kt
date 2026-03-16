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
import com.securecall.app.BuildConfig
import com.securecall.app.R
import com.securecall.app.config.FeatureProviderRegistry
import com.securecall.app.config.TierManager

class SettingsFragment : PreferenceFragmentCompat() {

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

        // Upgrade button (only for FREE tier)
        findPreference<Preference>("pref_upgrade")?.isVisible = BuildConfig.BILLING_ENABLED

        // Activation code section
        configureActivationCode(effectiveTier)

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

        // About section links
        setupAboutLinks()

        // Licenses placeholder
        findPreference<Preference>("pref_licenses")?.summary = "Apache 2.0, MIT, BSD"
    }

    override fun onResume() {
        super.onResume()
        // Refresh SecureCall ID summary in case it changed
        val prefs = requireContext().getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
        findPreference<Preference>("pref_client_id")?.summary = prefs.getString("client_id", "Not registered")
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

    private fun configureActivationCode(effectiveTier: String) {
        val isUpgraded = effectiveTier != "FREE"
        val codePref = findPreference<EditTextPreference>("pref_activation_code")
        val activateButton = findPreference<Preference>("pref_activate_button")

        if (isUpgraded) {
            // Already Pro/Premium — hide input, show status
            codePref?.isVisible = false
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
                isEnabled = true
                if (!preferenceManager.sharedPreferences!!.contains("pref_block_screenshots")) {
                    isChecked = true
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

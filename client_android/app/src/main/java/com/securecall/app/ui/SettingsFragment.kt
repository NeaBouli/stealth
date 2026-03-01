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

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Tier display
        findPreference<Preference>("pref_tier")?.summary = BuildConfig.TIER

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

        // Security features (read-only display based on tier)
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

        // SecureCall ID (tap to copy) — read fresh from SharedPreferences each time
        val prefs = requireContext().getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
        findPreference<Preference>("pref_client_id")?.apply {
            summary = prefs.getString("client_id", "Not registered")
            setOnPreferenceClickListener {
                val freshId = requireContext().getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("client_id", "Not registered") ?: "Not registered"
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SecureCall ID", freshId))
                android.widget.Toast.makeText(requireContext(), "ID copied: $freshId", android.widget.Toast.LENGTH_SHORT).show()
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

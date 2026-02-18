package com.securecall.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
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

            // ─── Anti-Recording Settings ────────────────────────
            configureAntiRecordingSettings(fp)
        }

        // Upgrade button (only for FREE tier)
        findPreference<Preference>("pref_upgrade")?.isVisible = BuildConfig.BILLING_ENABLED
    }

    /**
     * Configure anti-recording protection settings based on tier.
     *
     * - FREE:    All toggles available, defaults OFF
     * - PRO:     All toggles available, defaults ON
     * - PREMIUM: All forced ON, toggles disabled (not user-changeable)
     */
    private fun configureAntiRecordingSettings(fp: com.securecall.app.config.FeatureProvider) {
        val isPremium = fp.tier == "PREMIUM"
        val isPro = fp.tier == "PRO"

        // Block Screenshots toggle
        findPreference<SwitchPreferenceCompat>("pref_block_screenshots")?.apply {
            if (isPremium) {
                isChecked = true
                isEnabled = false
                summary = getString(R.string.pref_block_screenshots_premium)
            } else {
                isEnabled = true
                // PRO defaults ON, FREE defaults OFF
                if (!preferenceManager.sharedPreferences!!.contains("pref_block_screenshots")) {
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
            } else {
                isEnabled = true
                if (!preferenceManager.sharedPreferences!!.contains("pref_exclusive_mic")) {
                    isChecked = isPro
                }
            }
        }

        // Detect Screen Recording toggle
        findPreference<SwitchPreferenceCompat>("pref_detect_recording")?.apply {
            if (isPremium) {
                isChecked = true
                isEnabled = false
                summary = getString(R.string.always_on)
            } else {
                isEnabled = true
                if (!preferenceManager.sharedPreferences!!.contains("pref_detect_recording")) {
                    isChecked = isPro
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

package com.securecall.app.vpn

import android.content.Intent
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.securecall.app.R
import com.securecall.app.ui.SettingsFragment

object VpnFeature {
    private const val KEY_CATEGORY = "premium_vpn_category"
    private const val KEY_ENABLED = "premium_vpn_switch"
    private const val KEY_STATUS = "premium_vpn_status"
    private const val KEY_CONFIG = "premium_vpn_config"

    fun configure(
        fragment: SettingsFragment,
        permissionLauncher: ActivityResultLauncher<Intent>
    ) {
        val context = fragment.requireContext()
        VpnConfigStore.cleanupLegacyUiPreferences(context)
        if (fragment.findPreference<PreferenceCategory>(KEY_CATEGORY) != null) {
            refresh(fragment)
            return
        }

        val category = PreferenceCategory(context).apply {
            key = KEY_CATEGORY
            title = context.getString(R.string.premium_vpn_category)
            isIconSpaceReserved = false
        }
        fragment.preferenceScreen.addPreference(category)

        category.addPreference(SwitchPreferenceCompat(context).apply {
            key = KEY_ENABLED
            title = context.getString(R.string.premium_vpn_enabled)
            summary = context.getString(R.string.premium_vpn_enabled_summary)
            setIcon(R.drawable.ic_shield)
            isPersistent = false
            isChecked = VpnConfigStore.isEnabled(context)
            setOnPreferenceChangeListener { preference, newValue ->
                val enabled = newValue as Boolean
                if (!enabled) {
                    PremiumVpnController.stop(context)
                    preference.summary = context.getString(R.string.premium_vpn_enabled_summary)
                    fragment.view?.postDelayed({ refresh(fragment) }, 250)
                    return@setOnPreferenceChangeListener true
                }
                if (!VpnConfigStore.hasConfig(context)) {
                    Toast.makeText(context, R.string.premium_vpn_config_required, Toast.LENGTH_LONG).show()
                    return@setOnPreferenceChangeListener false
                }
                val permissionIntent = PremiumVpnController.permissionIntent(context)
                if (permissionIntent != null) {
                    permissionLauncher.launch(permissionIntent)
                } else {
                    PremiumVpnController.start(context)
                }
                fragment.view?.postDelayed({ refresh(fragment) }, 500)
                true
            }
        })

        category.addPreference(Preference(context).apply {
            key = KEY_STATUS
            title = context.getString(R.string.premium_vpn_status)
            setIcon(R.drawable.ic_lock)
            isSelectable = false
        })

        category.addPreference(Preference(context).apply {
            key = KEY_CONFIG
            title = context.getString(R.string.premium_vpn_config)
            setIcon(R.drawable.ic_settings)
            setOnPreferenceClickListener {
                showConfigDialog(fragment)
                true
            }
        })
        refresh(fragment)
    }

    fun refresh(fragment: SettingsFragment) {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        fragment.findPreference<SwitchPreferenceCompat>(KEY_ENABLED)?.isChecked =
            VpnConfigStore.isEnabled(context)
        fragment.findPreference<Preference>(KEY_STATUS)?.summary = when (PremiumVpnState.status) {
            PremiumVpnState.Status.OFF -> context.getString(R.string.premium_vpn_status_off)
            PremiumVpnState.Status.CONNECTING -> context.getString(R.string.premium_vpn_status_connecting)
            PremiumVpnState.Status.ACTIVE -> context.getString(R.string.premium_vpn_status_active)
            PremiumVpnState.Status.ERROR -> context.getString(R.string.premium_vpn_status_error)
        }
        fragment.findPreference<Preference>(KEY_CONFIG)?.summary =
            if (VpnConfigStore.hasConfig(context)) {
                context.getString(R.string.premium_vpn_config_saved)
            } else {
                context.getString(R.string.premium_vpn_config_missing)
            }
    }

    fun onPermissionResult(fragment: SettingsFragment, resultCode: Int) {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        if (PremiumVpnController.permissionGranted(resultCode)) {
            PremiumVpnController.start(context)
        } else {
            VpnConfigStore.setEnabled(context, false)
            Toast.makeText(context, R.string.premium_vpn_consent_denied, Toast.LENGTH_LONG).show()
        }
        fragment.view?.postDelayed({ refresh(fragment) }, 500)
    }

    private fun showConfigDialog(fragment: SettingsFragment) {
        val context = fragment.requireContext()
        val existing = VpnConfigStore.load(context)
        val fields = ConfigFields(context).apply { populate(existing) }

        val pasteButton = Button(context).apply {
            text = context.getString(R.string.premium_vpn_paste)
            setOnClickListener { showPasteDialog(fragment, fields) }
        }
        fields.container.addView(pasteButton, 0)

        val scroll = ScrollView(context).apply { addView(fields.container) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.premium_vpn_config_title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.premium_vpn_clear, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val config = WireGuardConfigParser.fromFields(
                    endpoint = fields.endpoint.text.toString(),
                    port = fields.port.text.toString(),
                    serverPublicKey = fields.serverPublicKey.text.toString(),
                    clientPrivateKey = fields.clientPrivateKey.text.toString(),
                    existingPrivateKey = existing?.clientPrivateKey.orEmpty(),
                    dns = fields.dns.text.toString(),
                    allowedIps = fields.allowedIps.text.toString(),
                    clientAddress = fields.clientAddress.text.toString()
                )
                if (config == null) {
                    Toast.makeText(context, R.string.premium_vpn_invalid_config, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!VpnConfigStore.save(context, config)) {
                    Toast.makeText(context, R.string.premium_vpn_save_failed, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                refresh(fragment)
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                PremiumVpnController.stop(context)
                VpnConfigStore.clear(context)
                Toast.makeText(context, R.string.premium_vpn_cleared, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                refresh(fragment)
            }
        }
        dialog.show()
    }

    private fun showPasteDialog(fragment: SettingsFragment, fields: ConfigFields) {
        val context = fragment.requireContext()
        val input = EditText(context).apply {
            minLines = 10
            maxLines = 16
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(false)
        }
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.premium_vpn_paste)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val parsed = WireGuardConfigParser.parse(
                    input.text.toString(),
                    VpnConfigStore.load(context)?.clientPrivateKey.orEmpty()
                )
                if (parsed == null) {
                    Toast.makeText(context, R.string.premium_vpn_invalid_config, Toast.LENGTH_LONG).show()
                } else {
                    fields.populate(parsed)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class ConfigFields(context: android.content.Context) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
        }
        val endpoint = field(context, R.string.premium_vpn_endpoint_hint)
        val port = field(context, R.string.premium_vpn_port_hint).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val serverPublicKey = field(context, R.string.premium_vpn_server_key_hint)
        val clientPrivateKey = field(context, R.string.premium_vpn_private_key_hint).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val dns = field(context, R.string.premium_vpn_dns_hint)
        val allowedIps = field(context, R.string.premium_vpn_allowed_ips_hint)
        val clientAddress = field(context, R.string.premium_vpn_client_address_hint)

        init {
            listOf(endpoint, port, serverPublicKey, clientPrivateKey, dns, allowedIps, clientAddress)
                .forEach { container.addView(it) }
        }

        fun populate(config: WireGuardConfigData?) {
            if (config == null) {
                port.setText("51820")
                dns.setText("1.1.1.1")
                allowedIps.setText("0.0.0.0/0")
                clientAddress.setText("10.66.66.2/32")
                return
            }
            endpoint.setText(config.endpoint)
            port.setText(config.port.toString())
            serverPublicKey.setText(config.serverPublicKey)
            clientPrivateKey.text?.clear()
            dns.setText(config.dns)
            allowedIps.setText(config.allowedIps)
            clientAddress.setText(config.clientAddress)
        }

        private fun field(context: android.content.Context, hintRes: Int): EditText =
            EditText(context).apply {
                hint = context.getString(hintRes)
                minHeight = (48 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
    }
}

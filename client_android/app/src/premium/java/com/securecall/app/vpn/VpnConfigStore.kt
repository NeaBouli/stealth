package com.securecall.app.vpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.preference.PreferenceManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object VpnConfigStore {
    private const val PREFS = "securecall_prefs"
    private const val KEY_ALIAS = "securecall_premium_vpn_config"
    private const val KEY_ENABLED = "premium_vpn_enabled"
    private const val KEY_ENDPOINT = "vpn_server_endpoint"
    private const val KEY_PORT = "vpn_server_port"
    private const val KEY_SERVER_PUBLIC = "vpn_server_public_key"
    private const val KEY_CLIENT_PRIVATE = "vpn_client_private_key_encrypted"
    private const val LEGACY_CLIENT_PRIVATE = "vpn_client_private_key"
    private const val KEY_DNS = "vpn_dns"
    private const val KEY_ALLOWED_IPS = "vpn_allowed_ips"
    private const val KEY_CLIENT_ADDRESS = "vpn_client_address"

    @Synchronized
    fun save(context: Context, config: WireGuardConfigData): Boolean {
        val encryptedPrivateKey = encrypt(config.clientPrivateKey) ?: return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENDPOINT, config.endpoint)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_SERVER_PUBLIC, config.serverPublicKey)
            .putString(KEY_CLIENT_PRIVATE, encryptedPrivateKey)
            .remove(LEGACY_CLIENT_PRIVATE)
            .putString(KEY_DNS, config.dns)
            .putString(KEY_ALLOWED_IPS, config.allowedIps)
            .putString(KEY_CLIENT_ADDRESS, config.clientAddress)
            .commit()
    }

    @Synchronized
    fun load(context: Context): WireGuardConfigData? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endpoint = prefs.getString(KEY_ENDPOINT, null)?.trim().orEmpty()
        val serverPublicKey = prefs.getString(KEY_SERVER_PUBLIC, null)?.trim().orEmpty()
        val encrypted = prefs.getString(KEY_CLIENT_PRIVATE, null)
        var clientPrivateKey = encrypted?.let(::decrypt).orEmpty()

        if (clientPrivateKey.isEmpty()) {
            val legacy = prefs.getString(LEGACY_CLIENT_PRIVATE, null)?.trim().orEmpty()
            if (legacy.isNotEmpty()) {
                val migrated = WireGuardConfigData(
                    endpoint = endpoint,
                    port = prefs.getInt(KEY_PORT, 51820),
                    serverPublicKey = serverPublicKey,
                    clientPrivateKey = legacy,
                    dns = prefs.getString(KEY_DNS, "1.1.1.1").orEmpty(),
                    allowedIps = prefs.getString(KEY_ALLOWED_IPS, "0.0.0.0/0").orEmpty(),
                    clientAddress = prefs.getString(KEY_CLIENT_ADDRESS, "10.66.66.2/32").orEmpty()
                )
                if (!save(context, migrated)) {
                    prefs.edit().remove(LEGACY_CLIENT_PRIVATE).commit()
                    return null
                }
                clientPrivateKey = legacy
            }
        }

        if (endpoint.isEmpty() || serverPublicKey.isEmpty() || clientPrivateKey.isEmpty()) return null
        return WireGuardConfigData(
            endpoint = endpoint,
            port = prefs.getInt(KEY_PORT, 51820),
            serverPublicKey = serverPublicKey,
            clientPrivateKey = clientPrivateKey,
            dns = prefs.getString(KEY_DNS, "1.1.1.1").orEmpty().ifEmpty { "1.1.1.1" },
            allowedIps = prefs.getString(KEY_ALLOWED_IPS, "0.0.0.0/0").orEmpty()
                .ifEmpty { "0.0.0.0/0" },
            clientAddress = prefs.getString(KEY_CLIENT_ADDRESS, "10.66.66.2/32").orEmpty()
                .ifEmpty { "10.66.66.2/32" }
        )
    }

    fun hasConfig(context: Context): Boolean = load(context) != null

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ENABLED) && prefs.contains("vpn_enabled")) {
            val legacyEnabled = prefs.getBoolean("vpn_enabled", false)
            prefs.edit().putBoolean(KEY_ENABLED, legacyEnabled).remove("vpn_enabled").apply()
            return legacyEnabled
        }
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ENABLED)
            .remove(KEY_ENDPOINT)
            .remove(KEY_PORT)
            .remove(KEY_SERVER_PUBLIC)
            .remove(KEY_CLIENT_PRIVATE)
            .remove(LEGACY_CLIENT_PRIVATE)
            .remove(KEY_DNS)
            .remove(KEY_ALLOWED_IPS)
            .remove(KEY_CLIENT_ADDRESS)
            .remove("vpn_enabled")
            .remove("vpn_kill_switch")
            .remove("esim_wireguard_mode")
            .commit()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    fun cleanupLegacyUiPreferences(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove("pref_vpn_enabled")
            .remove("pref_vpn_kill_switch")
            .remove("pref_esim_routing")
            .apply()
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(value: String): String? = runCatching {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}

package com.securecall.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Detects install source and opens the correct update channel.
 * - Google Play installs → opens Play Store
 * - Sideload/F-Droid → opens stealthx.tech/download
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PLAY_PACKAGE = "com.android.vending"
    private const val PLAY_URL = "https://play.google.com/store/apps/details?id="
    private const val SIDELOAD_URL = "https://stealthx.tech"

    enum class InstallSource { PLAY_STORE, SIDELOAD }

    fun getInstallSource(context: Context): InstallSource {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            Log.d(TAG, "Install source: $installer")
            if (installer == PLAY_PACKAGE) InstallSource.PLAY_STORE else InstallSource.SIDELOAD
        } catch (e: Exception) {
            Log.w(TAG, "Could not detect install source: ${e.message}")
            InstallSource.SIDELOAD
        }
    }

    fun openUpdate(context: Context) {
        val source = getInstallSource(context)
        val intent = when (source) {
            InstallSource.PLAY_STORE -> {
                try {
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).apply {
                        setPackage(PLAY_PACKAGE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } catch (e: Exception) {
                    Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL + context.packageName)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
            InstallSource.SIDELOAD -> {
                Intent(Intent.ACTION_VIEW, Uri.parse(SIDELOAD_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
        Log.d(TAG, "Opening update: source=$source, intent=$intent")
        context.startActivity(intent)
    }
}

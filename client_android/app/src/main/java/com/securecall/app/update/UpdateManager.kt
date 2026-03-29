package com.securecall.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.securecall.app.BuildConfig

/**
 * Opens the correct update channel based on install source.
 * - F-Droid flavor → GitHub Releases
 * - ADB/Sideload (installer=null) → GitHub Releases
 * - Play Store install → Play Store
 * - Any other known installer → Play Store
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PLAY_PACKAGE = "com.android.vending"
    private const val PLAY_ID = "com.securecall.app.free"
    private const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PLAY_ID"
    private const val GITHUB_RELEASES_URL = "https://github.com/NeaBouli/stealth/releases/latest"

    fun openUpdate(context: Context) {
        val url = getUpdateUrl(context)
        Log.d(TAG, "Opening update URL: $url")
        if (url.startsWith("market://")) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(PLAY_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Play Store app not available: ${e.message}")
            }
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun getUpdateUrl(context: Context): String {
        // F-Droid flavor → GitHub
        if (BuildConfig.APPLICATION_ID.endsWith(".fdroid")) {
            Log.d(TAG, "F-Droid flavor → GitHub")
            return GITHUB_RELEASES_URL
        }

        val installer = getInstallerPackage(context)
        Log.d(TAG, "Install source: $installer")

        return when {
            // ADB/Sideload → GitHub
            installer == null -> GITHUB_RELEASES_URL
            // Play Store → market:// intent
            installer == PLAY_PACKAGE -> "market://details?id=$PLAY_ID"
            // Any other store → Play Store web URL
            else -> PLAY_URL
        }
    }

    fun getUpdateLabel(): String = "Check for Updates"

    private fun getInstallerPackage(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine install source: ${e.message}")
            null
        }
    }
}

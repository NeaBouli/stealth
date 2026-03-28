package com.securecall.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.securecall.app.BuildConfig

/**
 * Opens the correct update channel based on install source.
 * - Installed via Google Play → Play Store
 * - F-Droid flavor or sideloaded APK → GitHub Releases
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PLAY_PACKAGE = "com.android.vending"
    private const val PLAY_ID = "com.securecall.app.free"
    private const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PLAY_ID"
    private const val GITHUB_RELEASES_URL = "https://github.com/NeaBouli/stealth/releases/latest"

    fun openUpdate(context: Context) {
        if (isPlayStoreInstall(context)) {
            Log.d(TAG, "Play Store install → opening Play Store for $PLAY_ID")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_ID")).apply {
                    setPackage(PLAY_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Play Store app not available, opening browser: ${e.message}")
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } else {
            // F-Droid, sideload, or APK install → GitHub Releases
            Log.d(TAG, "Non-Play install → opening GitHub Releases")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun getUpdateLabel(): String {
        return "Check for Updates"
    }

    /**
     * Check if the app was installed from Google Play Store.
     * Returns false for F-Droid, sideloaded APKs, or debug installs.
     */
    private fun isPlayStoreInstall(context: Context): Boolean {
        // F-Droid flavor is never Play Store
        if (BuildConfig.APPLICATION_ID.endsWith(".fdroid")) return false
        return try {
            val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            Log.d(TAG, "Install source: $installer")
            installer == PLAY_PACKAGE
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine install source: ${e.message}")
            false
        }
    }
}

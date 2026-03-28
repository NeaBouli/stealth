package com.securecall.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.securecall.app.BuildConfig

/**
 * Opens the correct update channel based on build flavor and type.
 * - F-Droid flavor → GitHub Releases
 * - Debug build (ADB/developer) → GitHub Releases
 * - Release build (Play Store users) → Play Store
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PLAY_PACKAGE = "com.android.vending"
    private const val PLAY_ID = "com.securecall.app.free"
    private const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PLAY_ID"
    private const val GITHUB_RELEASES_URL = "https://github.com/NeaBouli/stealth/releases/latest"

    /** Get the correct update URL based on flavor and build type. */
    fun getUpdateUrl(): String {
        return when {
            BuildConfig.APPLICATION_ID.endsWith(".fdroid") -> GITHUB_RELEASES_URL
            BuildConfig.DEBUG -> GITHUB_RELEASES_URL
            else -> PLAY_URL
        }
    }

    fun openUpdate(context: Context) {
        when {
            // F-Droid flavor → GitHub
            BuildConfig.APPLICATION_ID.endsWith(".fdroid") -> {
                Log.d(TAG, "F-Droid flavor → GitHub Releases")
                openUrl(context, GITHUB_RELEASES_URL)
            }
            // Debug build (ADB/developer testing) → GitHub
            BuildConfig.DEBUG -> {
                Log.d(TAG, "Debug build → GitHub Releases")
                openUrl(context, GITHUB_RELEASES_URL)
            }
            // Release build (Play Store end users) → Play Store
            else -> {
                Log.d(TAG, "Release build → Play Store")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_ID")).apply {
                        setPackage(PLAY_PACKAGE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Play Store app not available: ${e.message}")
                    openUrl(context, PLAY_URL)
                }
            }
        }
    }

    fun getUpdateLabel(): String {
        return "Check for Updates"
    }

    private fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

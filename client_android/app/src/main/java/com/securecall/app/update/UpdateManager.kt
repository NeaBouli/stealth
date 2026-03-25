package com.securecall.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.securecall.app.BuildConfig

/**
 * Opens the correct update channel.
 * - Free/Pro/Premium flavors → always Google Play (com.securecall.app.free listing)
 * - F-Droid flavor → stealthx.tech
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PLAY_PACKAGE = "com.android.vending"
    private const val PLAY_ID = "com.securecall.app.free"
    private const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PLAY_ID"
    private const val SIDELOAD_URL = "https://stealthx.tech"

    fun openUpdate(context: Context) {
        val isFdroid = BuildConfig.APPLICATION_ID.endsWith(".fdroid")
        if (isFdroid) {
            Log.d(TAG, "F-Droid flavor → opening stealthx.tech")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SIDELOAD_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        }

        // All other flavors → Google Play Store
        Log.d(TAG, "Opening Play Store for $PLAY_ID")
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
    }

    fun getUpdateLabel(): String {
        return if (BuildConfig.APPLICATION_ID.endsWith(".fdroid")) "Open stealthx.tech" else "Open Google Play"
    }
}

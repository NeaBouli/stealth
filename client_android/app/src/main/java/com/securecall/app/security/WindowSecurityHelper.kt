package com.securecall.app.security

import android.app.Activity
import android.view.WindowManager
import androidx.preference.PreferenceManager

/**
 * Centralized FLAG_SECURE logic so every Activity behaves consistently.
 *
 * Rules:
 *   FREE    → never set FLAG_SECURE
 *   PREMIUM → always set FLAG_SECURE
 *   PRO     → follow user toggle "pref_block_screenshots" (default: ON)
 *   FDROID  → same as FREE (no tier enforcement)
 */
object WindowSecurityHelper {

    @JvmStatic
    fun applyFlagSecure(activity: Activity) {
        try {
            val tier = com.securecall.app.config.TierManager.getCurrentTier(activity)
            val window = activity.window ?: return

            when {
                tier == "FREE" || tier == "FDROID" -> {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                tier == "PREMIUM" -> {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE)
                }
                else -> {
                    // PRO: follow user toggle (default ON)
                    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
                    if (prefs.getBoolean("pref_block_screenshots", true)) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        } catch (e: Exception) {
            // Fail-safe: apply FLAG_SECURE
            activity.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

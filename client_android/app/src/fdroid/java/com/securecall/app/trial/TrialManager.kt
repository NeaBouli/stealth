package com.securecall.app.trial

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * F-Droid 30-day trial manager.
 * After trial expires, outgoing calls are blocked for FREE tier users.
 * Incoming calls still work. Upgrade via activation code or IFR token.
 */
object TrialManager {
    private const val TAG = "TrialManager"
    private const val PREFS = "fdroid_trial"
    private const val KEY_INSTALL_DATE = "install_date"
    private const val TRIAL_DAYS = 30

    fun getInstallDate(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var date = prefs.getLong(KEY_INSTALL_DATE, 0L)
        if (date == 0L) {
            date = System.currentTimeMillis()
            prefs.edit().putLong(KEY_INSTALL_DATE, date).apply()
            Log.d(TAG, "Trial started: $date")
        }
        return date
    }

    fun isTrialActive(context: Context): Boolean {
        val days = getDaysUsed(context)
        return days < TRIAL_DAYS
    }

    fun getDaysRemaining(context: Context): Long {
        val days = getDaysUsed(context)
        return maxOf(0, TRIAL_DAYS.toLong() - days)
    }

    fun getDaysUsed(context: Context): Long {
        val installDate = getInstallDate(context)
        return (System.currentTimeMillis() - installDate) / (1000 * 60 * 60 * 24)
    }

    fun getTrialStatusText(context: Context): String {
        val remaining = getDaysRemaining(context)
        return if (remaining > 0) {
            "$remaining days remaining"
        } else {
            "Trial expired — enter activation code or lock IFR tokens"
        }
    }

    /**
     * Shows a non-cancelable dialog when trial has expired.
     * Offers: Enter Activation Code, Lock IFR Tokens, Learn More.
     */
    fun showTrialExpiredDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        AlertDialog.Builder(activity)
            .setTitle("\uD83D\uDD12 Trial Period Ended")
            .setMessage(
                "Your 30-day free trial has ended.\n\n" +
                "To continue making calls, choose one of the following options:"
            )
            .setCancelable(false)
            .setPositiveButton("Enter Activation Code") { _, _ ->
                try {
                    val intent = Intent()
                    intent.setClassName(activity.packageName, "com.securecall.app.SettingsActivity")
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open Settings: ${e.message}")
                }
            }
            .setNeutralButton("Lock IFR Tokens") { _, _ ->
                try {
                    val intent = Intent()
                    intent.setClassName(activity.packageName, "com.securecall.app.SettingsActivity")
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open Settings: ${e.message}")
                }
            }
            .setNegativeButton("Learn More") { _, _ ->
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://stealthx.tech/wiki/ifr-unlock.html"))
                )
            }
            .show()
    }

    /**
     * Returns true if the trial expired dialog should be shown at app start.
     * Only for FREE tier users whose trial has expired.
     */
    fun shouldShowExpiredDialog(context: Context): Boolean {
        if (isTrialActive(context)) return false
        val tier = com.securecall.app.config.TierManager.getCurrentTier(context)
        return tier == "FREE"
    }
}

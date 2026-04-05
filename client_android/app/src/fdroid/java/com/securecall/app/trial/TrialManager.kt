package com.securecall.app.trial

import android.content.Context
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
}

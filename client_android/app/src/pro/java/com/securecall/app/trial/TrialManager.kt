package com.securecall.app.trial

import android.content.Context

/** Trial stub — no trial restrictions for this flavor. */
object TrialManager {
    fun isTrialActive(context: Context): Boolean = true
    fun getDaysRemaining(context: Context): Long = Long.MAX_VALUE
    fun getTrialStatusText(context: Context): String = ""
}

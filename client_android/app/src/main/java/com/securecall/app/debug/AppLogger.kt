package com.securecall.app.debug

import android.util.Log
import com.securecall.app.config.FeatureProviderRegistry

/**
 * Application-level logger that respects the flavor's LOGGING_LEVEL.
 *
 * Levels (most to least verbose):
 *   DEBUG      -> d, i, w, e all allowed
 *   WARN       -> w, e only
 *   ERROR_ONLY -> e only
 *
 * For new code going forward. Existing Log.* calls are stripped
 * via ProGuard -assumenosideeffects in release builds.
 */
object AppLogger {

    private fun level(): String =
        try { FeatureProviderRegistry.get().loggingLevel } catch (_: Exception) { "DEBUG" }

    fun d(tag: String, msg: String) {
        if (level() == "DEBUG") Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (level() == "DEBUG") Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        val l = level()
        if (l == "DEBUG" || l == "WARN") Log.w(tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
    }
}

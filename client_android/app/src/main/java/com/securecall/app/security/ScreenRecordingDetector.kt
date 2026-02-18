package com.securecall.app.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.function.Consumer

/**
 * Detects active screen recording on Android 11+ (API 31+).
 *
 * Uses Activity.registerActivityLifecycleCallbacks with
 * WindowManager screen capture callbacks when available,
 * and falls back to process-based detection on older APIs.
 */
class ScreenRecordingDetector(private val context: Context) {

    private val TAG = "ScreenRecordDetector"

    @Volatile
    private var isRecording = false
    private var callback: Any? = null // Store callback reference for unregistration

    /** Callback invoked when screen recording state changes. */
    var onRecordingStateChanged: ((Boolean) -> Unit)? = null

    /**
     * Start monitoring for screen recording.
     * On API 34+: Uses WindowManager screen capture callback.
     * On older APIs: Falls back to process-based detection.
     */
    fun startMonitoring(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            startScreenCaptureCallback(activity)
        } else {
            // Fallback: check running processes for recording indicators
            isRecording = checkProcessBasedRecording()
            if (isRecording) {
                onRecordingStateChanged?.invoke(true)
            }
        }
        Log.d(TAG, "Screen recording monitoring started (API ${Build.VERSION.SDK_INT})")
    }

    /**
     * API 34+ (Android 14): Register screen capture callback.
     */
    private fun startScreenCaptureCallback(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val cb = Consumer<Int> { state ->
                    // state != 0 means capture is active
                    val capturing = state != 0
                    if (capturing != isRecording) {
                        isRecording = capturing
                        Log.w(TAG, "Screen capture state changed: recording=$capturing")
                        onRecordingStateChanged?.invoke(capturing)
                    }
                }
                // Use reflection to call registerScreenCaptureCallback on API 34+
                val method = Activity::class.java.getMethod(
                    "registerScreenCaptureCallback",
                    java.util.concurrent.Executor::class.java,
                    Activity.ScreenCaptureCallback::class.java
                )
                val screenCaptureCallback = object : Activity.ScreenCaptureCallback {
                    override fun onScreenCaptured() {
                        isRecording = true
                        Log.w(TAG, "Screen capture DETECTED via callback")
                        onRecordingStateChanged?.invoke(true)
                    }
                }
                method.invoke(activity, activity.mainExecutor, screenCaptureCallback)
                callback = screenCaptureCallback
                Log.d(TAG, "Registered API 34 ScreenCaptureCallback")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register ScreenCaptureCallback, using fallback", e)
                isRecording = checkProcessBasedRecording()
            }
        }
    }

    /**
     * Fallback: Check running processes for screen recording indicators.
     */
    private fun checkProcessBasedRecording(): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val processes = am.runningAppProcesses ?: return false

            val recorderIndicators = listOf(
                "screenrecord", "screen_record", "screen.record",
                "screencap", "screen_cap",
                "mediaprojection",
                "scrcpy",
                "vysor",
                "apowerrec", "apower",
                "az.screen.recorder",
                "com.duapps.recorder",
                "com.kimcy929.screenrecorder",
                "com.hecorat.screenrecorder"
            )

            return processes.any { process ->
                val name = process.processName.lowercase()
                recorderIndicators.any { indicator -> name.contains(indicator) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check running processes", e)
            return false
        }
    }

    /**
     * Check if display is being captured (overlay detection).
     * Works on Android 10+ (API 29).
     */
    fun isDisplayCaptured(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE)
                    as android.hardware.display.DisplayManager
                val displays = displayManager.displays
                // Check for virtual displays (screen recording creates virtual displays)
                return displays.any { display ->
                    val flags = display.flags
                    // FLAG_PRESENTATION (1 << 3) indicates a virtual/presentation display
                    (flags and android.view.Display.FLAG_PRESENTATION) != 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check display capture", e)
            }
        }
        return isRecording
    }

    fun isRecordingDetected(): Boolean = isRecording || checkProcessBasedRecording()

    fun stopMonitoring(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34 && callback != null) {
            try {
                val method = Activity::class.java.getMethod(
                    "unregisterScreenCaptureCallback",
                    Activity.ScreenCaptureCallback::class.java
                )
                method.invoke(activity, callback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister ScreenCaptureCallback", e)
            }
            callback = null
        }
        Log.d(TAG, "Screen recording monitoring stopped")
    }
}

package com.securecall.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Detects installed call recording applications.
 *
 * Scans the device for known call recording apps that could
 * capture audio during encrypted SecureCall conversations.
 */
class CallRecordingDetector(private val context: Context) {

    private val TAG = "CallRecordDetector"

    /** Known call recording app packages. */
    private val knownRecordingApps = mapOf(
        "com.nll.acr" to "ACR Phone",
        "com.nll.cb" to "ACR Call Recorder",
        "com.catalinagroup.callrecorder" to "Cube Call Recorder",
        "com.appstar.callrecorder" to "Automatic Call Recorder",
        "com.boldbeast.recorder" to "BoldBeast Call Recorder",
        "com.callrecorder.auto" to "Auto Call Recorder",
        "com.record.call.recording" to "Call Recorder",
        "com.callrecord.auto" to "Call Record Auto",
        "com.skvalex.callrecorder" to "Call Recorder skvalex",
        "com.smsrobot.callrecorder" to "RMC Call Recorder",
        "com.google.android.apps.recorder" to "Google Recorder",
        "com.samsung.android.app.callrecording" to "Samsung Call Recording",
        "com.roe.recorder" to "Otter Voice",
        "com.rev.recorder" to "Rev Call Recorder",
        "com.tapeacall.android" to "TapeACall",
        "com.recordmycalls" to "RecordMyCall",
        "io.callbox.recorder" to "Callbox Recorder",
        "com.threegalaxies.callrecorderpro" to "Call Recorder Pro",
        "com.caller.recorder" to "Call Recorder Automatic",
        "com.truecaller" to "Truecaller (recording feature)"
    )

    /** Recording-related intent actions. */
    private val recordingKeywords = listOf(
        "call_record", "callrecord", "call.record",
        "voice_record", "voicerecord",
        "audio_record", "audiorecord",
        "screen_record", "screenrecord"
    )

    data class DetectionResult(
        val detectedApps: List<DetectedApp>,
        val hasRecordingApps: Boolean
    )

    data class DetectedApp(
        val packageName: String,
        val appName: String,
        val isKnown: Boolean
    )

    /**
     * Scan for installed call recording apps.
     */
    fun detect(): DetectionResult {
        val detected = mutableListOf<DetectedApp>()

        // Check known recording apps
        for ((packageName, appName) in knownRecordingApps) {
            if (isInstalled(packageName)) {
                detected.add(DetectedApp(packageName, appName, isKnown = true))
                Log.w(TAG, "Call recording app detected: $appName ($packageName)")
            }
        }

        // Heuristic: Check for apps with recording-related package names
        try {
            val installedPackages = context.packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA
            )
            for (appInfo in installedPackages) {
                val pkg = appInfo.packageName.lowercase()
                if (recordingKeywords.any { pkg.contains(it) } &&
                    !detected.any { it.packageName == appInfo.packageName }
                ) {
                    val label = try {
                        context.packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        appInfo.packageName
                    }
                    detected.add(DetectedApp(appInfo.packageName, label, isKnown = false))
                    Log.w(TAG, "Potential recording app (heuristic): $label (${appInfo.packageName})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan installed apps", e)
        }

        return DetectionResult(
            detectedApps = detected,
            hasRecordingApps = detected.isNotEmpty()
        )
    }

    private fun isInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

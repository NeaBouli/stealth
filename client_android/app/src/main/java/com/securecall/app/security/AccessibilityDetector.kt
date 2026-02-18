package com.securecall.app.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager

/**
 * Detects potentially malicious accessibility services and notification listeners.
 *
 * Spy apps often abuse accessibility services to capture screen content,
 * keystrokes, and audio. This detector identifies suspicious services
 * and warns or blocks based on the active security tier.
 */
class AccessibilityDetector(private val context: Context) {

    private val TAG = "AccessibilityDetector"

    /** Known legitimate accessibility services (whitelisted). */
    private val whitelistedPackages = setOf(
        "com.google.android.marvin.talkback",    // Google TalkBack
        "com.google.android.accessibility",       // Google Accessibility Suite
        "com.samsung.accessibility",              // Samsung Accessibility
        "com.samsung.android.accessibility.talkback",
        "com.android.talkback",
        "com.google.android.apps.accessibility.voiceaccess", // Voice Access
        "com.android.switchaccess",               // Switch Access
        "com.google.android.apps.accessibility.maui", // Google Lookout
        "com.android.server.accessibility"        // System accessibility
    )

    /** Known spy/surveillance app packages. */
    private val knownSpyAppPackages = setOf(
        "com.teamviewer",                         // TeamViewer
        "com.teamviewer.host",
        "com.teamviewer.quicksupport.market",
        "com.anydesk.anydeskandroid",             // AnyDesk
        "com.realvnc.viewer.android",             // VNC Viewer
        "com.splashtop.remote",                   // Splashtop
        "com.bomgar.honeywell",                   // BeyondTrust
        "com.logmein.rescuemobile",               // LogMeIn Rescue
        "com.mspy",                               // mSpy
        "com.flexispy",                           // FlexiSpy
        "com.cerberusapp.cerberus",               // Cerberus
        "com.prey",                               // Prey Anti-Theft
        "com.kidguard",                           // KidGuard
        "com.cocospy",                            // Cocospy
        "com.spyic",                              // Spyic
        "com.hoverwatch",                         // Hoverwatch
        "com.eyezy",                              // EyeZy
        "com.clevguard",                          // ClevGuard
        "org.xdadev.screenstreammirrorpro"        // Screen Stream
    )

    data class DetectionResult(
        val suspiciousServices: List<String>,
        val spyAppsDetected: List<String>,
        val suspiciousNotificationListeners: List<String>,
        val isSafe: Boolean
    )

    /**
     * Run full accessibility scan.
     */
    fun detect(): DetectionResult {
        val suspiciousServices = detectSuspiciousAccessibilityServices()
        val spyApps = detectSpyApps()
        val notificationListeners = detectSuspiciousNotificationListeners()

        val isSafe = suspiciousServices.isEmpty() && spyApps.isEmpty() && notificationListeners.isEmpty()

        if (!isSafe) {
            Log.w(TAG, "Security threats detected: " +
                "services=${suspiciousServices.size}, " +
                "spyApps=${spyApps.size}, " +
                "notifListeners=${notificationListeners.size}")
        }

        return DetectionResult(
            suspiciousServices = suspiciousServices,
            spyAppsDetected = spyApps,
            suspiciousNotificationListeners = notificationListeners,
            isSafe = isSafe
        )
    }

    /**
     * Check enabled accessibility services for suspicious (non-whitelisted) entries.
     */
    private fun detectSuspiciousAccessibilityServices(): List<String> {
        val suspicious = mutableListOf<String>()
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            for (service in enabledServices) {
                val packageName = service.resolveInfo?.serviceInfo?.packageName ?: continue
                if (packageName !in whitelistedPackages &&
                    !packageName.startsWith("com.android.") &&
                    !packageName.startsWith("com.google.android.")
                ) {
                    // Check capabilities — services that can read screen are high risk
                    val canReadScreen = (service.capabilities and
                        AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0
                    val canPerformGestures = (service.capabilities and
                        AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0

                    if (canReadScreen || canPerformGestures) {
                        suspicious.add(packageName)
                        Log.w(TAG, "Suspicious accessibility service: $packageName " +
                            "(readScreen=$canReadScreen, gestures=$canPerformGestures)")
                    }

                    // Known spy app?
                    if (packageName in knownSpyAppPackages) {
                        if (packageName !in suspicious) suspicious.add(packageName)
                        Log.e(TAG, "KNOWN SPY APP detected in accessibility services: $packageName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check accessibility services", e)
        }
        return suspicious
    }

    /**
     * Detect known spy app packages installed on the device.
     */
    private fun detectSpyApps(): List<String> {
        val detected = mutableListOf<String>()
        val pm = context.packageManager

        for (packageName in knownSpyAppPackages) {
            try {
                pm.getPackageInfo(packageName, 0)
                detected.add(packageName)
                Log.e(TAG, "KNOWN SPY APP installed: $packageName")
            } catch (_: Exception) {
                // Package not installed — good
            }
        }
        return detected
    }

    /**
     * Check notification listener services for suspicious apps.
     * Apps with notification access can read all notifications including call details.
     */
    private fun detectSuspiciousNotificationListeners(): List<String> {
        val suspicious = mutableListOf<String>()
        try {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return emptyList()

            val listenerPackages = enabledListeners.split(":").mapNotNull { component ->
                component.split("/").firstOrNull()?.trim()
            }.filter { it.isNotEmpty() }.distinct()

            for (packageName in listenerPackages) {
                // Skip system and known-safe packages
                if (packageName.startsWith("com.android.") ||
                    packageName.startsWith("com.google.android.") ||
                    packageName == context.packageName
                ) continue

                // Flag known spy apps
                if (packageName in knownSpyAppPackages) {
                    suspicious.add(packageName)
                    Log.e(TAG, "SPY APP has notification access: $packageName")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check notification listeners", e)
        }
        return suspicious
    }
}

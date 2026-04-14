package com.securecall.app.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.securecall.app.BuildConfig

/**
 * Update-entry-point used from the UI ("Check for Updates" button + auto-check
 * on MainActivity start). Dispatches based on the original install source:
 *
 *   - Play Store        → open market:// (Play Store shows "Update" button)
 *   - F-Droid           → open fdroid client on the SecureCall package page
 *   - Sideload (null)   → in-app GitHub check + download + install flow
 *   - Other             → Play Store web URL fallback
 *
 * Threading: [checkAndPromptUpdate] is safe to call from the UI thread; all
 * network I/O is moved to a background thread internally.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PLAY_PACKAGE = "com.android.vending"
    private const val FDROID_PACKAGE = "org.fdroid.fdroid"
    private const val FDROID_BASIC_PACKAGE = "org.fdroid.basic"
    private const val PLAY_ID = "com.securecall.app.free"
    private const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PLAY_ID"
    private const val GITHUB_RELEASES_URL = "https://github.com/NeaBouli/stealth/releases/latest"
    private const val FDROID_PAGE_URL = "https://f-droid.org/packages/com.securecall.app.fdroid/"

    private const val PREFS = "update_prefs"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_SKIP_CODE = "skip_version_code"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L  // 24 h

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Label for the Settings entry. Kept for backwards compat.
     */
    @JvmStatic
    fun getUpdateLabel(): String =
        if (UpdateChecker.isAdbOnlyFlavor()) "Updates (ADB only)" else "Check for Updates"

    /**
     * **Manual** check — invoked from Settings → "Check for Updates".
     * Always runs the network check and ALWAYS shows feedback to the user
     * (either the update dialog or a "You're up-to-date" toast).
     */
    @JvmStatic
    fun checkAndPromptUpdate(activity: Activity) {
        if (UpdateChecker.isAdbOnlyFlavor()) {
            Log.w(TAG, "Manual check — ADB-only flavor '${BuildConfig.FLAVOR}'")
            Toast.makeText(activity, "Update via ADB only for this build", Toast.LENGTH_LONG).show()
            return
        }
        val source = InstallSource.resolve(activity)
        Log.w(TAG, "Manual check — install source: $source")

        when (source) {
            InstallSource.PLAY_STORE -> openPlayStore(activity)
            InstallSource.FDROID -> openFDroid(activity)
            InstallSource.SIDELOAD, InstallSource.OTHER_STORE -> runInAppCheck(activity, manual = true)
        }
    }

    /**
     * **Auto** check — invoked from MainActivity.onCreate. Throttled to once
     * per 24 h via [PREFS]. Only shows UI when an update is actually available
     * — stays silent otherwise (no nagging).
     *
     * Skips entirely for Play Store installs (Play Store handles its own
     * update notifications) and for F-Droid installs (F-Droid client polls
     * the repo and notifies users natively).
     */
    @JvmStatic
    fun maybeAutoCheck(activity: Activity) {
        if (UpdateChecker.isAdbOnlyFlavor()) {
            Log.w(TAG, "Auto-check skipped — ADB-only flavor '${BuildConfig.FLAVOR}'")
            return
        }
        val source = InstallSource.resolve(activity)
        if (source != InstallSource.SIDELOAD && source != InstallSource.OTHER_STORE) {
            Log.w(TAG, "Auto-check skipped for source=$source (handled by store)")
            return
        }

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (now - last < CHECK_INTERVAL_MS) {
            Log.w(TAG, "Auto-check throttled — next in ${((CHECK_INTERVAL_MS - (now - last)) / 3600_000L)}h")
            return
        }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        Log.w(TAG, "Auto-check firing (source=$source)")
        runInAppCheck(activity, manual = false)
    }

    // ─── Legacy fallback (unchanged behaviour) ─────────────────

    /**
     * Legacy entry point kept for backwards compatibility. Prefer
     * [checkAndPromptUpdate] in new code — it gives users a richer flow
     * than a blind browser redirect.
     */
    @JvmStatic
    fun openUpdate(context: Context) {
        if (UpdateChecker.isAdbOnlyFlavor()) {
            Toast.makeText(context, "Update via ADB only for this build", Toast.LENGTH_LONG).show()
            return
        }
        // If caller is an Activity, use the full in-app check flow (direct APK download)
        if (context is Activity) {
            val source = InstallSource.resolve(context)
            Log.d(TAG, "openUpdate via Activity — source: $source")
            when (source) {
                InstallSource.PLAY_STORE -> openPlayStore(context)
                InstallSource.FDROID -> openFDroid(context)
                InstallSource.SIDELOAD, InstallSource.OTHER_STORE -> runInAppCheck(context, manual = true)
            }
            return
        }
        // Fallback for non-Activity contexts (service, broadcast receiver)
        val url = getUpdateUrl(context)
        Log.d(TAG, "Opening update URL (non-Activity fallback): $url")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    @JvmStatic
    fun getUpdateUrl(context: Context): String {
        if (BuildConfig.APPLICATION_ID.endsWith(".fdroid")) {
            return FDROID_PAGE_URL
        }
        return when (InstallSource.resolve(context)) {
            InstallSource.PLAY_STORE -> "market://details?id=$PLAY_ID"
            InstallSource.FDROID -> FDROID_PAGE_URL
            InstallSource.SIDELOAD -> GITHUB_RELEASES_URL
            InstallSource.OTHER_STORE -> PLAY_URL
        }
    }

    // ─── In-app flow (sideload) ────────────────────────────────

    private fun runInAppCheck(activity: Activity, manual: Boolean) {
        if (manual) {
            Toast.makeText(activity, "Checking for updates…", Toast.LENGTH_SHORT).show()
        }
        Thread({
            val info = UpdateChecker.checkLatest()
            Handler(Looper.getMainLooper()).post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (info == null) {
                    Log.w(TAG, "Update check result: up-to-date (v${BuildConfig.VERSION_NAME})")
                    if (manual) {
                        Toast.makeText(
                            activity,
                            "You're up-to-date (v${BuildConfig.VERSION_NAME}).",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@post
                }
                Log.w(TAG, "Update check result: update available vC${info.versionCode}")
                if (!manual && shouldSkip(activity, info.versionCode)) return@post
                showUpdateDialog(activity, info)
            }
        }, "update-check").start()
    }

    private fun shouldSkip(context: Context, code: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SKIP_CODE, -1) == code
    }

    private fun showUpdateDialog(activity: Activity, info: UpdateInfo) {
        val versionLabel = if (info.versionName.isNotEmpty())
            "v${info.versionName} (build ${info.versionCode})"
        else
            "build ${info.versionCode}"
        val sizeMb = if (info.apkSize > 0)
            " · ${info.apkSize / 1_000_000}\u202FMB"
        else
            ""
        val message = buildString {
            append("A new version is available:\n\n")
            append(versionLabel)
            append(sizeMb)
            if (info.changelog.isNotBlank()) {
                append("\n\nWhat's new:\n")
                append(info.changelog.trim())
            }
            append(
                "\n\nTap \"Open Download\" to get the APK from GitHub. " +
                    "Your browser or download manager handles the rest — open the .apk file when " +
                    "it's finished to install."
            )
        }

        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(message)
            .setPositiveButton("Open Download") { _, _ ->
                openBrowser(activity, info.apkUrl)
            }
            .setNeutralButton("Release Page") { _, _ ->
                openBrowser(activity, info.releaseUrl)
            }
            .setNegativeButton("Later") { d, _ -> d.dismiss() }
            .setCancelable(true)
            .show()
    }

    /**
     * Hand the URL off to whatever the user's default browser or download
     * manager is. This is intentionally the ONLY way sideload users reach the
     * new APK — the app never writes or launches the APK itself, which means
     * no REQUEST_INSTALL_PACKAGES permission is needed (Play Console compliance).
     */
    private fun openBrowser(activity: Activity, url: String) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "No browser available to open $url: ${t.message}")
            Toast.makeText(
                activity,
                "No browser available — visit github.com/NeaBouli/stealth/releases",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // ─── Store routing helpers ──────────────────────────────────

    private fun openPlayStore(activity: Activity) {
        val marketUri = Uri.parse("market://details?id=$PLAY_ID")
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, marketUri).apply {
                    setPackage(PLAY_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Play Store app not available: ${t.message}")
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun openFDroid(activity: Activity) {
        // Prefer the F-Droid client directly (so the user lands on the package
        // page, one tap to update). Fall back to the web URL if F-Droid is not
        // installed.
        val fdroidAppId = "com.securecall.app.fdroid"
        val clientUri = Uri.parse("fdroid.app://details?id=$fdroidAppId")
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, clientUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (t: Throwable) {
            Log.w(TAG, "F-Droid client not available: ${t.message}")
        }
        activity.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_PAGE_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ─── Install-source detection ───────────────────────────────

    private enum class InstallSource {
        PLAY_STORE, FDROID, SIDELOAD, OTHER_STORE;

        companion object {
            fun resolve(context: Context): InstallSource {
                if (BuildConfig.APPLICATION_ID.endsWith(".fdroid")) return FDROID
                val installer = getInstallerPackage(context)
                return when (installer) {
                    null, "", "com.android.shell", "adb" -> SIDELOAD
                    PLAY_PACKAGE -> PLAY_STORE
                    FDROID_PACKAGE, FDROID_BASIC_PACKAGE -> FDROID
                    else -> OTHER_STORE
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstallerPackage(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine install source: ${e.message}")
            null
        }
    }
}

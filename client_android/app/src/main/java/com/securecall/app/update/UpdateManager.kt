package com.securecall.app.update

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
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
    fun getUpdateLabel(): String = "Check for Updates"

    /**
     * **Manual** check — invoked from Settings → "Check for Updates".
     * Always runs the network check and ALWAYS shows feedback to the user
     * (either the update dialog or a "You're up-to-date" toast).
     */
    @JvmStatic
    fun checkAndPromptUpdate(activity: Activity) {
        val source = InstallSource.resolve(activity)
        Log.d(TAG, "Manual check — install source: $source")

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
        val source = InstallSource.resolve(activity)
        if (source != InstallSource.SIDELOAD && source != InstallSource.OTHER_STORE) {
            Log.d(TAG, "Auto-check skipped for source=$source (handled by store)")
            return
        }

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (now - last < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Auto-check throttled — next in ${((CHECK_INTERVAL_MS - (now - last)) / 3600_000L)}h")
            return
        }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

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
        val url = getUpdateUrl(context)
        Log.d(TAG, "Opening update URL: $url")
        if (url.startsWith("market://")) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(PLAY_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Play Store app not available: ${e.message}")
            }
        }
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
                    if (manual) {
                        Toast.makeText(
                            activity,
                            "You're up-to-date (v${BuildConfig.VERSION_NAME}).",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@post
                }
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
        }

        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(message)
            .setPositiveButton("Download & Install") { _, _ ->
                startDownload(activity, info)
            }
            .setNeutralButton("Changelog") { _, _ ->
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .setNegativeButton("Later") { d, _ -> d.dismiss() }
            .setCancelable(true)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun startDownload(activity: Activity, info: UpdateInfo) {
        // ProgressDialog is deprecated but perfectly fine for a short,
        // one-off progress indicator on a modal action. No need for a
        // fancy custom dialog just for this.
        val progress = ProgressDialog(activity).apply {
            setTitle("Updating SecureCall")
            setMessage("Downloading new version…")
            setIndeterminate(true)
            setCancelable(false)
            isIndeterminate = true
            show()
        }

        UpdateDownloader.download(
            context = activity,
            apkUrl = info.apkUrl,
            onProgress = { /* enqueue id — DownloadManager shows its own notification */ },
            onComplete = { file ->
                Handler(Looper.getMainLooper()).post {
                    try {
                        progress.dismiss()
                    } catch (_: Throwable) {
                    }
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    if (file == null) {
                        Toast.makeText(
                            activity,
                            "Download failed — check your network and try again.",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@post
                    }
                    when (val result = UpdateInstaller.install(activity, file)) {
                        UpdateInstaller.Result.Launched -> Unit
                        UpdateInstaller.Result.NeedsPermission -> {
                            AlertDialog.Builder(activity)
                                .setTitle("One more step")
                                .setMessage(
                                    "Android asks you to allow installs from SecureCall. " +
                                        "Toggle \"Allow from this source\" and return here, then tap " +
                                        "\"Install Now\" to finish."
                                )
                                .setPositiveButton("Install Now") { _, _ ->
                                    // After user grants the permission and returns, re-run the install.
                                    val retry = UpdateInstaller.install(activity, file)
                                    if (retry is UpdateInstaller.Result.NeedsPermission) {
                                        Toast.makeText(
                                            activity,
                                            "Permission still not granted.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                        is UpdateInstaller.Result.SignatureMismatch -> {
                            AlertDialog.Builder(activity)
                                .setTitle("Security check failed")
                                .setMessage(
                                    "The downloaded update is not signed by the SecureCall team. " +
                                        "The update has been discarded.\n\n${result.detail}"
                                )
                                .setPositiveButton("OK", null)
                                .show()
                            UpdateDownloader.cancel(activity)
                        }
                        is UpdateInstaller.Result.Failed -> {
                            Toast.makeText(
                                activity,
                                "Install failed: ${result.reason}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
        )
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

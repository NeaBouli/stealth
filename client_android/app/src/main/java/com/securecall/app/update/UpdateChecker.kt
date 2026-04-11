package com.securecall.app.update

import android.util.Log
import com.securecall.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Queries GitHub Releases API for the latest SecureCall release and parses
 * the `vC(\d+)` version code from the APK asset filename.
 *
 * Runs on a background thread — caller must NOT invoke from the main thread.
 * Safe to call repeatedly: the caller should throttle (e.g. once per 24 h).
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    /**
     * GitHub API endpoint for the latest release of the stealth repo.
     * Returns a single release object with `tag_name`, `name`, `body`, `assets[]`.
     */
    private const val RELEASES_URL =
        "https://api.github.com/repos/NeaBouli/stealth/releases/latest"

    /**
     * Pattern that matches our release asset filenames — extracts the
     * numeric versionCode from `…-vC(\d+)\.apk`.
     */
    private val VC_PATTERN = Regex("""-vC(\d+)\.apk$""")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetch the latest release and return an [UpdateInfo] if it's newer than
     * the currently installed [BuildConfig.VERSION_CODE]; otherwise null.
     *
     * Returns null on network errors, parse errors, or when the current
     * version is already up-to-date.
     */
    @JvmStatic
    @Suppress("TooGenericExceptionCaught")
    fun checkLatest(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("User-Agent", "SecureCall/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API returned ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                parseRelease(body)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Update check failed: ${t.message}")
            null
        }
    }

    /**
     * Parse a GitHub release JSON object into [UpdateInfo].
     *
     * Walks `assets[]` and picks the first `.apk` with a `vC(\d+)` suffix.
     * Returns null if no matching asset is found or if the parsed versionCode
     * is not strictly greater than [BuildConfig.VERSION_CODE].
     */
    internal fun parseRelease(json: String): UpdateInfo? {
        val root = JSONObject(json)
        val releaseUrl = root.optString("html_url", "")
        val releaseName = root.optString("name", root.optString("tag_name", ""))
        val body = root.optString("body", "")
        val changelog = body.take(500)

        val assets = root.optJSONArray("assets") ?: return null

        var bestCode = -1
        var bestUrl = ""
        var bestSize = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0L)
            if (!name.endsWith(".apk", ignoreCase = true)) continue

            // Only free-tier APK — don't pick up fdroid/pro/premium if they ever land
            // in the same release. Matches `securecall-free-*.apk` or `app-free-*.apk`.
            if (!name.contains("free", ignoreCase = true)) continue

            val match = VC_PATTERN.find(name) ?: continue
            val code = match.groupValues[1].toIntOrNull() ?: continue
            if (code > bestCode) {
                bestCode = code
                bestUrl = url
                bestSize = size
            }
        }

        if (bestCode <= 0 || bestUrl.isEmpty()) {
            Log.w(TAG, "No matching free APK asset in release")
            return null
        }

        if (bestCode <= BuildConfig.VERSION_CODE) {
            Log.d(
                TAG,
                "Already up-to-date (installed=${BuildConfig.VERSION_CODE}, latest=$bestCode)"
            )
            return null
        }

        // Try to extract versionName from release title ("SecureCall v1.0.15 (vC36)")
        // or from the filename ("securecall-free-v1.0.15-vC36.apk").
        val versionName = Regex("""v(\d+\.\d+\.\d+)""").find(releaseName)?.groupValues?.get(1)
            ?: Regex("""v(\d+\.\d+\.\d+)""").find(bestUrl)?.groupValues?.get(1)
            ?: ""

        Log.d(
            TAG,
            "Update available: installed=${BuildConfig.VERSION_CODE} → latest=$bestCode ($versionName)"
        )

        return UpdateInfo(
            versionCode = bestCode,
            versionName = versionName,
            apkUrl = bestUrl,
            apkSize = bestSize,
            releaseUrl = releaseUrl,
            changelog = changelog,
        )
    }
}

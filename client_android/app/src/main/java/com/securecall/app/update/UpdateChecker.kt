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
     * Returns true for flavors distributed only via ADB (premium, pro).
     * These builds have no public release assets on GitHub.
     */
    @JvmStatic
    fun isAdbOnlyFlavor(): Boolean =
        BuildConfig.FLAVOR == "premium" || BuildConfig.FLAVOR == "pro"

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

    /** Fallback: extract versionCode from release body text "versionCode XX" or "vC XX" */
    private val BODY_VC_PATTERN = Regex("""(?:versionCode|vC)\s*[:=]?\s*(\d+)""")

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
        if (isAdbOnlyFlavor()) {
            Log.w(TAG, "Flavor '${BuildConfig.FLAVOR}' is ADB-only — skipping update check")
            return null
        }
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

            // Pick the APK matching the installed flavor (free → free).
            // Premium/pro never reach here (blocked by isAdbOnlyFlavor() above).
            if (!name.contains(BuildConfig.FLAVOR, ignoreCase = true)) continue

            val match = VC_PATTERN.find(name) ?: continue
            val code = match.groupValues[1].toIntOrNull() ?: continue
            if (code > bestCode) {
                bestCode = code
                bestUrl = url
                bestSize = size
            }
        }

        // Fallback: if no vC pattern in asset names, try to get versionCode from release body
        if (bestCode <= 0 && bestUrl.isEmpty()) {
            // Find any APK matching our flavor (without vC requirement)
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                val size = asset.optLong("size", 0L)
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (!name.contains(BuildConfig.FLAVOR, ignoreCase = true)) continue
                bestUrl = url
                bestSize = size
                break
            }
            // Extract versionCode from body text
            val bodyMatch = BODY_VC_PATTERN.find(body)
            if (bodyMatch != null) {
                bestCode = bodyMatch.groupValues[1].toIntOrNull() ?: -1
            }
        }

        if (bestCode <= 0 || bestUrl.isEmpty()) {
            Log.w(TAG, "No matching '${BuildConfig.FLAVOR}' APK asset in release")
            return null
        }

        if (bestCode <= BuildConfig.VERSION_CODE) {
            Log.w(
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

        Log.w(
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

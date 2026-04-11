package com.securecall.app.update

/**
 * Metadata about an available update.
 *
 * @property versionCode Android `versionCode` of the new build (parsed from release
 *                       asset filename, e.g. `securecall-free-v1.0.15-vC36.apk` → 36).
 * @property versionName Human-readable version (e.g. `1.0.15`).
 * @property apkUrl      Direct download URL of the release APK asset.
 * @property apkSize     File size in bytes (0 if unknown).
 * @property releaseUrl  URL of the GitHub release page (for "Changelog" button).
 * @property changelog   Truncated release notes (first ~500 chars).
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkSize: Long,
    val releaseUrl: String,
    val changelog: String,
)

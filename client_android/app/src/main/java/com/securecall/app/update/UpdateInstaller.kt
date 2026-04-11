package com.securecall.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * Launches the system package installer for a downloaded update APK.
 *
 * Key responsibilities:
 *   1. **Signature verification** — compares the SHA-256 of the downloaded APK's
 *      signing certificate against the currently installed app's certificate.
 *      Refuses to install if they don't match. This is the crucial guardrail
 *      that prevents MITM / tampered updates from being installed blindly.
 *   2. **FileProvider grant** — wraps the downloaded file in a content:// URI
 *      with `FLAG_GRANT_READ_URI_PERMISSION` so the installer can read it.
 *   3. **Install-permission prompt** — on API 26+ (Oreo), the app needs the
 *      `REQUEST_INSTALL_PACKAGES` permission. If the user hasn't granted it,
 *      we open the "Install unknown apps" settings page so they can toggle it.
 */
object UpdateInstaller {

    private const val TAG = "UpdateInstaller"

    /**
     * Result of [install].
     */
    sealed class Result {
        object Launched : Result()
        object NeedsPermission : Result()
        data class SignatureMismatch(val detail: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * Verify [apkFile] and launch the install intent if everything checks out.
     */
    fun install(context: Context, apkFile: File): Result {
        if (!apkFile.exists() || apkFile.length() < 1024) {
            return Result.Failed("APK file missing or too small")
        }

        // 1) Signature verification
        val sigCheck = verifySignature(context, apkFile)
        if (sigCheck != null) {
            Log.e(TAG, "Signature check failed: $sigCheck")
            return Result.SignatureMismatch(sigCheck)
        }

        // 2) Install permission (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                // Open the settings page for this app so the user can toggle
                // "Allow from this source".
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (t: Throwable) {
                    Log.w(TAG, "Couldn't open unknown-sources settings: ${t.message}")
                }
                return Result.NeedsPermission
            }
        }

        // 3) Launch installer via FileProvider
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            context.startActivity(intent)
            Log.d(TAG, "Install intent launched for $apkFile")
            Result.Launched
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch installer: ${t.message}", t)
            Result.Failed(t.message ?: "unknown")
        }
    }

    /**
     * Compare the SHA-256 of the signing cert in [apkFile] with the cert of the
     * currently installed app. Returns null on match, or a human-readable
     * error string on mismatch / parse failure.
     */
    @Suppress("DEPRECATION")
    private fun verifySignature(context: Context, apkFile: File): String? {
        return try {
            val pm = context.packageManager
            val pkgName = context.packageName

            val installedDigest = certDigest(pm.getPackageInfo(pkgName, sigFlags()), pm, pkgName, installed = true)
                ?: return "installed cert unavailable"

            val apkInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, sigFlags())
                ?: return "could not parse APK"
            val apkDigest = certDigest(apkInfo, pm, apkFile.absolutePath, installed = false)
                ?: return "APK cert unavailable"

            if (installedDigest != apkDigest) {
                "SHA-256 mismatch: installed=${installedDigest.take(12)}… apk=${apkDigest.take(12)}…"
            } else {
                null
            }
        } catch (t: Throwable) {
            "verify threw: ${t.message}"
        }
    }

    private fun sigFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    /**
     * Extract the SHA-256 of the first signing certificate from a [PackageInfo].
     *
     * On API 28+ we prefer [PackageInfo.signingInfo]. On older versions we fall
     * back to the deprecated [PackageInfo.signatures] field.
     */
    @Suppress("DEPRECATION")
    private fun certDigest(
        info: PackageInfo?,
        @Suppress("UNUSED_PARAMETER") pm: PackageManager,
        @Suppress("UNUSED_PARAMETER") source: String,
        @Suppress("UNUSED_PARAMETER") installed: Boolean,
    ): String? {
        if (info == null) return null
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = info.signingInfo
            when {
                si == null -> info.signatures
                si.hasMultipleSigners() -> si.apkContentsSigners
                else -> si.signingCertificateHistory
            }
        } else {
            info.signatures
        }
        val first = sigs?.firstOrNull() ?: return null
        return sha256(first.toByteArray())
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val out = md.digest(bytes)
        return out.joinToString("") { "%02x".format(it) }
    }
}

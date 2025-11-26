package com.securecall.app.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * ANDROID-03
 * Basis-Sicherheitsmonitor (MVP-Version).
 *
 * Prüft nur passive Indikatoren:
 * - Developer Options aktiv
 * - USB Debugging
 * - Emulator-Erkennung
 *
 * Erweiterungen folgen in ANDROID-04/05/06.
 */
class SecureModeMonitor(private val ctx: Context) {

    data class Result(
        val developerMode: Boolean,
        val usbDebugging: Boolean,
        val emulator: Boolean
    )

    fun check(): Result {
        return Result(
            developerMode = isDeveloperEnabled(),
            usbDebugging = isUsbDebuggingEnabled(),
            emulator = isEmulator()
        )
    }

    private fun isDeveloperEnabled(): Boolean {
        return Settings.Global.getInt(
            ctx.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0
    }

    private fun isUsbDebuggingEnabled(): Boolean {
        return Settings.Global.getInt(
            ctx.contentResolver,
            Settings.Global.ADB_ENABLED, 0
        ) != 0
    }

    private fun isEmulator(): Boolean {
        val result =
            (Build.FINGERPRINT.contains("generic")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86"))
        return result
    }
}

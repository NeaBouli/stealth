package com.securecall.app.security

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import java.io.File

/**
 * ANDROID-04
 * Erweiterter Sicherheitsmonitor (MVP).
 *
 * Checks:
 * - Root Detection
 * - Screen Recorder running?
 * - Debugger connected?
 * - Hooking indicator files
 */
class SecureModeAdvanced(private val ctx: Context) {

    data class Result(
        val isRooted: Boolean,
        val screenRecording: Boolean,
        val debuggerAttached: Boolean,
        val hookingDetected: Boolean
    )

    fun check(): Result {
        return Result(
            isRooted = detectRoot(),
            screenRecording = detectScreenRecorder(),
            debuggerAttached = Debug.isDebuggerConnected(),
            hookingDetected = detectHooking()
        )
    }

    // --- Root detection (MVP) ---
    private fun detectRoot(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    // --- Screen Recording detection ---
    private fun detectScreenRecorder(): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return false
        return processes.any {
            it.processName.contains("recorder", true) ||
            it.processName.contains("screen", true)
        }
    }

    // --- Hooking detection (Frida/LSPosed indicators) ---
    private fun detectHooking(): Boolean {
        val suspects = arrayOf(
            "/data/local/tmp/frida",
            "/dev/binderfs/frida",
            "/system/lib/libsubstrate.so",
            "/system/lib64/libsubstrate.so",
            "/system/framework/lspd.jar"
        )
        return suspects.any { File(it).exists() }
    }
}

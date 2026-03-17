package com.securecall.app.security

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * STEALTH-DELETE: Emergency wipe of all app data.
 * Destroys all contacts, call history, settings, keys, and cached data.
 * After execution, the app behaves as if freshly installed.
 */
object StealthDeleteManager {
    private const val TAG = "STEALTH_DELETE"

    fun execute(context: Context) {
        Log.d(TAG, "STEALTH-DELETE initiated")

        // 1. Deregister from server (remove phone mapping)
        deregisterFromServer(context)

        // 2. Disconnect WebSocket
        try {
            val ws = com.securecall.app.net.WebSocketService.instance
            ws?.clearSession()
            ws?.stopSelf()
        } catch (_: Exception) {}

        // 3. Clear ALL SharedPreferences files
        clearAllSharedPreferences(context)

        // 4. Delete all files in internal storage
        deleteRecursive(context.filesDir)
        deleteRecursive(context.cacheDir)

        // 5. Delete databases (if any)
        for (db in context.databaseList()) {
            context.deleteDatabase(db)
        }

        // 6. Delete external cache
        context.externalCacheDir?.let { deleteRecursive(it) }

        // 7. Cancel all notifications
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancelAll()
        } catch (_: Exception) {}

        // 8. Clear clipboard (may contain SecureCall ID)
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {}

        Log.d(TAG, "STEALTH-DELETE complete — all data destroyed")

        // 9. Restart app (fresh install state → onboarding)
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun deregisterFromServer(context: Context) {
        try {
            val ws = com.securecall.app.net.WebSocketService.instance ?: return
            val prefs = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
            val clientId = prefs.getString("client_id", null) ?: return
            val json = """{"type":"DEREGISTER","clientId":"$clientId"}"""
            ws.sendMessage(json)
            Log.d(TAG, "DEREGISTER sent for $clientId")
        } catch (_: Exception) {}
    }

    private fun clearAllSharedPreferences(context: Context) {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        if (prefsDir.exists() && prefsDir.isDirectory) {
            prefsDir.listFiles()?.forEach { file ->
                val name = file.nameWithoutExtension
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                Log.d(TAG, "Cleared SharedPreferences: $name")
            }
        }
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        file.delete()
    }
}

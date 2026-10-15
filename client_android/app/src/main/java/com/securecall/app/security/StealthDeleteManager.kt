package com.securecall.app.security

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.securecall.app.net.WebSocketService
import java.io.File
import org.json.JSONObject

/**
 * The single STEALTH-DELETE implementation for settings and emergency broadcasts.
 * Removes all app SharedPreferences, databases, internal files, internal/external caches,
 * the signaling identity key, notifications and clipboard contents, then restarts for onboarding.
 * Server deregistration is attempted before local state is removed and never waits on network I/O.
 */
object StealthDeleteManager {
    private const val TAG = "STEALTH_DELETE"

    fun execute(context: Context) {
        val failures = wipeData(context)
        if (failures.isNotEmpty()) {
            // Do not log identities, preference names, contents or gesture state.
            Log.w(TAG, "STEALTH-DELETE steps failed: ${failures.joinToString()}")
        }

        try {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
            }
        } finally {
            // Drop in-memory state even if no launcher is available or the restart fails.
            Runtime.getRuntime().exit(0)
        }
    }

    internal fun wipeData(
        context: Context,
        destroyIdentity: () -> Unit = IdentitySigningKey::destroy,
    ): List<StealthDeleteContract.Step> = StealthDeleteContract.run { step ->
        when (step) {
            StealthDeleteContract.Step.DEREGISTER -> deregisterFromServer(context)
            StealthDeleteContract.Step.DISCONNECT -> {
                WebSocketService.instance?.let { ws ->
                    try {
                        ws.clearSession()
                    } finally {
                        ws.stopSelf()
                    }
                }
            }
            StealthDeleteContract.Step.IDENTITY_SIGNING_KEY -> destroyIdentity()
            StealthDeleteContract.Step.SHARED_PREFERENCES -> clearAllSharedPreferences(context)
            StealthDeleteContract.Step.DATABASES ->
                StealthDeleteContract.attemptAll(context.databaseList().asList()) { name ->
                    check(context.deleteDatabase(name)) { "Cannot remove wipe database" }
                }
            StealthDeleteContract.Step.FILES -> StealthDeleteContract.deleteContents(context.filesDir)
            StealthDeleteContract.Step.CACHE -> StealthDeleteContract.deleteContents(context.cacheDir)
            StealthDeleteContract.Step.EXTERNAL_CACHE -> context.externalCacheDirs.filterNotNull().let { dirs ->
                StealthDeleteContract.attemptAll(dirs) { StealthDeleteContract.deleteContents(it) }
            }
            StealthDeleteContract.Step.NOTIFICATIONS ->
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancelAll()
            StealthDeleteContract.Step.CLIPBOARD -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard?.clearPrimaryClip()
                } else {
                    clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }

    private fun deregisterFromServer(context: Context) {
        val ws = WebSocketService.instance ?: return
        val clientId = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
            .getString("client_id", null) ?: return
        ws.sendMessage(JSONObject().apply {
            put("type", "DEREGISTER")
            put("clientId", clientId)
        }.toString())
    }

    private fun clearAllSharedPreferences(context: Context) {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val names = StealthDeleteContract.preferenceNames(
            context.packageName,
            prefsDir.listFiles()?.map { it.name }.orEmpty(),
        )
        StealthDeleteContract.attemptAll(names) { name ->
            try {
                // Synchronously clear cached instances and finish pending apply() writes first.
                check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
            } finally {
                // Android also evicts its cache and removes both the XML and its backup.
                check(context.deleteSharedPreferences(name))
            }
        }
    }
}

package com.securecall.app.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BUG-027: Auto-start WebSocketService after device reboot.
 * Ensures SecureCall can receive calls immediately after boot
 * without the user having to open the app first.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            // Android 15+ (API 35): boot-starting a persistent dataSync FGS is not
            // allowed — incoming calls are delivered via FCM secure push instead.
            if (!ForegroundServicePolicy.allowsBootStart(android.os.Build.VERSION.SDK_INT)) {
                Log.d("BootReceiver", "API 35+ — skipping WebSocketService boot start, cancelling keep-alive")
                KeepAliveReceiver.cancel(context)
                return
            }
            if (!WebSocketService.isBackgroundServiceEnabled(context)) {
                Log.d("BootReceiver", "Background service disabled — skipping WebSocketService boot start")
                KeepAliveReceiver.cancel(context)
                return
            }
            Log.d("BootReceiver", "Device booted — starting WebSocketService")
            try {
                val serviceIntent = Intent(context, WebSocketService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start service after boot", e)
            }
            // NEA-180: Start Doze-tolerant keep-alive alarm chain after boot
            KeepAliveReceiver.scheduleNext(context)
        }
    }
}

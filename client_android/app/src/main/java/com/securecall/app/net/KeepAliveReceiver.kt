package com.securecall.app.net

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * NEA-180: Doze-safe keep-alive for WebSocketService.
 *
 * Uses setExactAndAllowWhileIdle() instead of setInexactRepeating() so the alarm
 * fires reliably during Doze maintenance windows, even in deep Doze (after 6+ h idle).
 * setInexactRepeating() intervals are stretched to hours in deep Doze, causing the
 * 30-minute PARTIAL_WAKE_LOCK in WebSocketService to expire before the next refresh.
 *
 * Each firing restarts (or pings) the foreground service and reschedules the next alarm.
 * Chain started from: WebSocketService.scheduleServiceRestart() + BootReceiver.
 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("KeepAliveReceiver", "Alarm fired — refreshing WebSocketService WakeLock")
        try {
            val serviceIntent = Intent(context, WebSocketService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e("KeepAliveReceiver", "Failed to start WebSocketService: ${e.message}")
        }
        scheduleNext(context)
    }

    companion object {
        private const val INTERVAL_MS = 12 * 60 * 1000L // 12 min — within 30-min WakeLock window

        fun scheduleNext(context: Context) {
            try {
                val pi = buildPendingIntent(context)
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + INTERVAL_MS,
                    pi
                )
                Log.d("KeepAliveReceiver", "Next alarm in ${INTERVAL_MS / 60_000}min")
            } catch (e: Exception) {
                Log.e("KeepAliveReceiver", "Failed to schedule alarm: ${e.message}")
            }
        }

        fun cancel(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.cancel(buildPendingIntent(context))
                Log.d("KeepAliveReceiver", "Alarm cancelled")
            } catch (e: Exception) {
                Log.e("KeepAliveReceiver", "Failed to cancel alarm: ${e.message}")
            }
        }

        private fun buildPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, KeepAliveReceiver::class.java)
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

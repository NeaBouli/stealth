package com.securecall.app.net

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * NEA-180: Doze-tolerant self-heal for WebSocketService.
 *
 * This alarm chain restarts or pings the foreground service if an OEM kills it.
 * The foreground service itself owns the PARTIAL_WAKE_LOCK while it is alive, so
 * this path is no longer a required refresh before a wake-lock timeout.
 *
 * Each firing restarts (or pings) the foreground service and reschedules the next alarm.
 * Chain started from: WebSocketService.scheduleServiceRestart() + BootReceiver.
 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("KeepAliveReceiver", "Alarm fired; ensuring WebSocketService is running")
        try {
            val serviceIntent = Intent(context, WebSocketService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e("KeepAliveReceiver", "Failed to start WebSocketService: ${e.message}")
        }
        scheduleNext(context)
    }

    companion object {
        private const val INTERVAL_MS = 12 * 60 * 1000L

        fun scheduleNext(context: Context) {
            try {
                val pi = buildPendingIntent(context)
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
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

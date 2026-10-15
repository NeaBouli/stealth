package com.securecall.app.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context

object IncomingCallNotifications {
    const val WS_NOTIFICATION_ID = 1002
    const val FCM_BACKUP_NOTIFICATION_ID = 9001

    fun silence(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter {
            it.id == WS_NOTIFICATION_ID || it.id == FCM_BACKUP_NOTIFICATION_ID
        }.forEach {
            val notification = Notification.Builder.recoverBuilder(context, it.notification)
                .setOnlyAlertOnce(true).build()
            notification.flags = notification.flags and Notification.FLAG_INSISTENT.inv()
            nm.notify(it.id, notification)
        }
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(WS_NOTIFICATION_ID)
        nm.cancel(FCM_BACKUP_NOTIFICATION_ID)
    }
}

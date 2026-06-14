package com.securecall.app.notifications

import android.app.NotificationManager
import android.content.Context

object IncomingCallNotifications {
    const val WS_NOTIFICATION_ID = 1002
    const val FCM_BACKUP_NOTIFICATION_ID = 9001

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(WS_NOTIFICATION_ID)
        nm.cancel(FCM_BACKUP_NOTIFICATION_ID)
    }
}

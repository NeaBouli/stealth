package com.securecall.app.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Incoming-call notifications are bound to their verified session: a session tag on the
 * fixed ids and a per-session PendingIntent identity (API 24 compatible). Cancel/silence
 * are scoped, so an old session can never dismiss a newer call's notification. Untagged
 * notifications posted by a pre-update process are a retained residual (no migration cancel).
 */
object IncomingCallNotifications {
    const val WS_NOTIFICATION_ID = 1002
    const val FCM_BACKUP_NOTIFICATION_ID = 9001
    private const val TAG_PREFIX = "securecall.call:"
    private const val SESSION_SCHEME = "securecall-call"

    fun tag(sessionId: String): String = TAG_PREFIX + sessionId

    /** PendingIntent request code; the session data URI keeps identities distinct on collision. */
    fun requestCode(sessionId: String): Int = sessionId.hashCode()

    /** Distinct `filterEquals` identity per session, so UPDATE_CURRENT never rewrites another call. */
    fun bindSession(intent: Intent, sessionId: String): Intent =
        intent.setData(Uri.fromParts(SESSION_SCHEME, sessionId, null))

    fun silence(context: Context, sessionId: String) {
        if (sessionId.isEmpty()) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val tag = tag(sessionId)
        nm.activeNotifications.filter {
            it.tag == tag && (it.id == WS_NOTIFICATION_ID || it.id == FCM_BACKUP_NOTIFICATION_ID)
        }.forEach {
            val notification = Notification.Builder.recoverBuilder(context, it.notification)
                .setOnlyAlertOnce(true).build()
            notification.flags = notification.flags and Notification.FLAG_INSISTENT.inv()
            nm.notify(it.tag, it.id, notification)
        }
    }

    fun cancel(context: Context, sessionId: String) {
        if (sessionId.isEmpty()) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val tag = tag(sessionId)
        nm.cancel(tag, WS_NOTIFICATION_ID)
        nm.cancel(tag, FCM_BACKUP_NOTIFICATION_ID)
    }
}

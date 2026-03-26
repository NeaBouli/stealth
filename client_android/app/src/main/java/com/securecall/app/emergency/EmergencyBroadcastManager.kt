package com.securecall.app.emergency

import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Locale

/**
 * Emergency Broadcast System for SecureCall.
 *
 * Privacy-preserving: Only a numeric template_id is transmitted.
 * All alert text is pre-installed on the device — nothing sensitive ever leaves the server.
 */
object EmergencyBroadcastManager {

    private const val TAG = "EmergencyBroadcast"

    enum class Severity { INFO, LOW, HIGH, CRITICAL }

    data class EmergencyTemplate(
        val icon: String,
        val titleEn: String,
        val titleDe: String,
        val bodyEn: String,
        val bodyDe: String,
        val severity: Severity,
        val dismissable: Boolean = true,
        val showUpdateButton: Boolean = false,
        val showStealthDelete: Boolean = false
    ) {
        fun title(): String = if (Locale.getDefault().language == "de") titleDe else titleEn
        fun body(): String = if (Locale.getDefault().language == "de") bodyDe else bodyEn
    }

    val TEMPLATES = mapOf(
        1 to EmergencyTemplate(
            icon = "\uD83D\uDD34", // red circle
            titleEn = "CRITICAL: Do Not Use SecureCall",
            titleDe = "KRITISCH: SecureCall nicht verwenden",
            bodyEn = "A security vulnerability has been detected. Stop all calls immediately and update the app now.",
            bodyDe = "Eine Sicherheitslücke wurde entdeckt. Beenden Sie alle Anrufe sofort und aktualisieren Sie die App.",
            severity = Severity.CRITICAL,
            dismissable = false,
            showUpdateButton = true
        ),
        2 to EmergencyTemplate(
            icon = "\uD83D\uDFE0", // orange circle
            titleEn = "Security Alert",
            titleDe = "Sicherheitswarnung",
            bodyEn = "A potential security compromise has been detected. Switch to backup communication channels immediately.",
            bodyDe = "Eine mögliche Sicherheitsgefährdung wurde festgestellt. Wechseln Sie sofort zu Backup-Kommunikationskanälen.",
            severity = Severity.HIGH,
            dismissable = false
        ),
        3 to EmergencyTemplate(
            icon = "\uD83D\uDFE1", // yellow circle
            titleEn = "Critical Update Required",
            titleDe = "Kritisches Update erforderlich",
            bodyEn = "A critical security update is available. Install it immediately to maintain secure communications.",
            bodyDe = "Ein kritisches Sicherheitsupdate ist verfügbar. Installieren Sie es sofort.",
            severity = Severity.HIGH,
            dismissable = true,
            showUpdateButton = true
        ),
        4 to EmergencyTemplate(
            icon = "\uD83D\uDD35", // blue circle
            titleEn = "Service Maintenance",
            titleDe = "Wartungsarbeiten",
            bodyEn = "Temporary service interruption in progress. Calls may be affected for a limited time.",
            bodyDe = "Vorübergehende Dienstunterbrechung. Anrufe können kurzzeitig beeinträchtigt sein.",
            severity = Severity.LOW,
            dismissable = true
        ),
        5 to EmergencyTemplate(
            icon = "\u26AB", // black circle
            titleEn = "STEALTH PROTOCOL ACTIVATED",
            titleDe = "STEALTH-PROTOKOLL AKTIVIERT",
            bodyEn = "Activate stealth protocol immediately. Consider uninstalling if you are in a high-risk situation.",
            bodyDe = "Aktivieren Sie sofort das Stealth-Protokoll. Erwägen Sie die Deinstallation in Hochrisikosituationen.",
            severity = Severity.CRITICAL,
            dismissable = false,
            showStealthDelete = true
        ),
        6 to EmergencyTemplate(
            icon = "\uD83D\uDCFB", // radio
            titleEn = "Emergency Broadcast",
            titleDe = "Notfallbenachrichtigung",
            bodyEn = "Turn on your radio or television immediately. Monitor official emergency channels for important information.",
            bodyDe = "Schalten Sie sofort Radio oder Fernsehen ein. Verfolgen Sie offizielle Notfallkanäle für wichtige Informationen.",
            severity = Severity.HIGH,
            dismissable = true
        ),
        7 to EmergencyTemplate(
            icon = "\u26A0\uFE0F", // warning
            titleEn = "Network Compromise Warning",
            titleDe = "Netzwerk-Kompromittierung",
            bodyEn = "Your current network may be monitored. Switch to a different network immediately. Use VPN if available.",
            bodyDe = "Ihr aktuelles Netzwerk könnte überwacht werden. Wechseln Sie sofort das Netzwerk. VPN aktivieren falls verfügbar.",
            severity = Severity.HIGH,
            dismissable = true
        ),
        8 to EmergencyTemplate(
            icon = "\uD83D\uDFE2", // green circle
            titleEn = "All Clear",
            titleDe = "Entwarnung",
            bodyEn = "Previous security alert has been resolved. Service is fully restored. You may resume normal use.",
            bodyDe = "Die vorherige Sicherheitswarnung wurde behoben. Der Dienst ist vollständig wiederhergestellt.",
            severity = Severity.INFO,
            dismissable = true
        ),
        9 to EmergencyTemplate(
            icon = "\uD83D\uDD04", // arrows counterclockwise
            titleEn = "Update Available",
            titleDe = "Update verf\u00FCgbar",
            bodyEn = "A new version of SecureCall is available on Google Play. Update now for the latest features and improvements.",
            bodyDe = "Eine neue Version von SecureCall ist im Google Play Store verf\u00FCgbar. Jetzt aktualisieren.",
            severity = Severity.INFO,
            dismissable = true,
            showUpdateButton = true
        ),
        10 to EmergencyTemplate(
            icon = "\uD83E\uDDEA", // test tube
            titleEn = "Beta Update Available",
            titleDe = "Beta-Update verf\u00FCgbar",
            bodyEn = "A new version of SecureCall is available.\n\nTo update:\n1. Open Google Play Store\n2. Tap your profile \u2192 Manage apps\n3. Find SecureCall \u2192 Update\n\nOr use your beta tester link from the invitation email.",
            bodyDe = "Eine neue Version von SecureCall ist verf\u00FCgbar.\n\nSo aktualisieren:\n1. Google Play Store \u00F6ffnen\n2. Profilbild \u2192 Apps verwalten\n3. SecureCall suchen \u2192 Aktualisieren\n\nOder den Beta-Tester-Link aus der Einladungs-Email nutzen.",
            severity = Severity.INFO,
            dismissable = true,
            showUpdateButton = true
        )
    )

    /**
     * Handle an incoming emergency broadcast. Only a template_id is received.
     * If app is in foreground → launch Activity directly.
     * If app is in background → show system notification (Samsung blocks bg activity starts).
     */
    fun handleBroadcast(context: Context, templateId: Int) {
        val template = TEMPLATES[templateId]
        if (template == null) {
            Log.w(TAG, "Unknown template_id: $templateId — ignoring")
            return
        }

        Log.d(TAG, "Emergency broadcast received: template=$templateId severity=${template.severity}")

        val intent = Intent(context, EmergencyBroadcastActivity::class.java).apply {
            putExtra("template_id", templateId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Try to start Activity — if it fails (background restriction), show notification
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start Activity from background — showing notification instead")
            showBroadcastNotification(context, template, templateId)
        }

        // Always show notification as backup (visible on lock screen)
        showBroadcastNotification(context, template, templateId)
    }

    private fun showBroadcastNotification(context: Context, template: EmergencyTemplate, templateId: Int) {
        val channelId = "emergency_broadcast"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Emergency Broadcasts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical security and update alerts"
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, EmergencyBroadcastActivity::class.java).apply {
            putExtra("template_id", templateId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = android.app.PendingIntent.getActivity(
            context, templateId, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.securecall.app.R.mipmap.ic_launcher)
            .setContentTitle("${template.icon} ${template.title()}")
            .setContentText(template.body())
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(template.body()))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        manager.notify(8000 + templateId, notification)
        Log.d(TAG, "Emergency notification shown for template=$templateId")
    }
}

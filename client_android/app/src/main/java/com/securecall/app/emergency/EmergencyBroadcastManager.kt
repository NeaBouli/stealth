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
        )
    )

    /**
     * Handle an incoming emergency broadcast. Only a template_id is received.
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
        context.startActivity(intent)
    }
}

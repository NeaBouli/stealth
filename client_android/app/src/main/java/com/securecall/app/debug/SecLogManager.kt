package com.securecall.app.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * SecLog — Diagnostic log manager for Pro/Premium users.
 * Collects WebSocket, FCM, Call, and Network events in a ring buffer.
 * Exportable as CSV via Share Sheet.
 */
object SecLogManager {

    private const val TAG = "SecLog"
    private const val MAX_ENTRIES = 1000
    private const val PREFS = "securecall_prefs"
    private const val KEY_ENABLED = "seclog_enabled"

    data class LogEntry(
        val timestamp: Long,
        val category: String,
        val message: String
    )

    private val buffer = Collections.synchronizedList(mutableListOf<LogEntry>())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            log("SYSTEM", "SecLog enabled")
        }
    }

    /** Log a diagnostic event. Only records if SecLog is enabled. */
    fun log(category: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), category, message)
        buffer.add(entry)
        // Trim to MAX_ENTRIES
        while (buffer.size > MAX_ENTRIES) {
            buffer.removeAt(0)
        }
        Log.d(TAG, "[$category] $message")
    }

    /** Log only if enabled for the given context. */
    fun logIfEnabled(context: Context, category: String, message: String) {
        if (isEnabled(context)) {
            log(category, message)
        }
    }

    fun getEntryCount(): Int = buffer.size

    fun clearLogs() {
        buffer.clear()
        Log.d(TAG, "Logs cleared")
    }

    /** Export logs as CSV and open Share Sheet. */
    fun exportCsv(context: Context) {
        if (buffer.isEmpty()) {
            android.widget.Toast.makeText(context, "No logs to export", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvFile = File(context.cacheDir, "seclog_${System.currentTimeMillis()}.csv")
            csvFile.bufferedWriter().use { writer ->
                writer.write("timestamp,category,message\n")
                synchronized(buffer) {
                    for (entry in buffer) {
                        val ts = dateFormat.format(Date(entry.timestamp))
                        val escapedMsg = entry.message.replace("\"", "\"\"")
                        writer.write("\"$ts\",\"${entry.category}\",\"$escapedMsg\"\n")
                    }
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SecureCall Diagnostic Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export SecLog"))
            Log.d(TAG, "CSV exported: ${buffer.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "CSV export failed", e)
            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

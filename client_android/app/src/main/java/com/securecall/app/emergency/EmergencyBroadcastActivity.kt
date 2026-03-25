package com.securecall.app.emergency

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.securecall.app.R

/**
 * Full-screen emergency alert overlay.
 * Non-dismissable for CRITICAL severity (no back button).
 * Shows localized template content based on template_id.
 */
class EmergencyBroadcastActivity : AppCompatActivity() {

    private var template: EmergencyBroadcastManager.EmergencyTemplate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val templateId = intent.getIntExtra("template_id", -1)
        template = EmergencyBroadcastManager.TEMPLATES[templateId]

        if (template == null) {
            finish()
            return
        }

        val t = template!!
        val bgColor = when (t.severity) {
            EmergencyBroadcastManager.Severity.CRITICAL -> 0xFF8B0000.toInt() // dark red
            EmergencyBroadcastManager.Severity.HIGH -> 0xFFCC6600.toInt()     // dark orange
            EmergencyBroadcastManager.Severity.LOW -> 0xFF1A1A2E.toInt()      // dark blue
            EmergencyBroadcastManager.Severity.INFO -> 0xFF0D3B0D.toInt()     // dark green
        }

        val pad = (24 * resources.displayMetrics.density).toInt()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            setPadding(pad, pad * 3, pad, pad)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Icon
        layout.addView(TextView(this).apply {
            text = t.icon
            textSize = 64f
            setPadding(0, 0, 0, pad)
        })

        // Title
        layout.addView(TextView(this).apply {
            text = t.title()
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, pad)
        })

        // Body
        layout.addView(TextView(this).apply {
            text = t.body()
            textSize = 18f
            setTextColor(0xFFDDDDDD.toInt())
            setPadding(0, 0, 0, pad * 2)
        })

        // Update button — auto-detects Play Store vs sideload
        if (t.showUpdateButton) {
            layout.addView(Button(this).apply {
                text = "Update Now"
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                minimumHeight = (48 * resources.displayMetrics.density).toInt()
                setOnClickListener {
                    com.securecall.app.update.UpdateManager.openUpdate(this@EmergencyBroadcastActivity)
                }
                setPadding(0, 0, 0, pad)
            })
        }

        // Stealth Delete button
        if (t.showStealthDelete) {
            layout.addView(Button(this).apply {
                text = "STEALTH DELETE"
                textSize = 16f
                setBackgroundColor(0xFF000000.toInt())
                setTextColor(0xFFFF0000.toInt())
                setOnClickListener {
                    // Trigger stealth delete — same as 5-tap reset
                    val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    val defaultPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@EmergencyBroadcastActivity)
                    defaultPrefs.edit().clear().apply()
                    // Clear all databases
                    for (db in databaseList()) { deleteDatabase(db) }
                    // Clear cache
                    cacheDir.deleteRecursively()
                    // Restart app
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
                setPadding(0, 0, 0, pad)
            })
        }

        // Dismiss button (only if dismissable)
        if (t.dismissable) {
            layout.addView(Button(this).apply {
                text = "I understand"
                textSize = 14f
                setOnClickListener { finish() }
            })
        } else {
            layout.addView(TextView(this).apply {
                text = if (t.showStealthDelete) "" else "This alert cannot be dismissed."
                textSize = 12f
                setTextColor(0xFF999999.toInt())
            })
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onBackPressed() {
        if (template?.dismissable == true) {
            super.onBackPressed()
        }
        // Block back button for non-dismissable alerts
    }
}

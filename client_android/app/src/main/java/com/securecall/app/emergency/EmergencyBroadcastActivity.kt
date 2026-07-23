package com.securecall.app.emergency

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.securecall.app.R
import com.securecall.app.ui.EdgeToEdgeHelper

/**
 * Full-screen emergency alert overlay.
 * Non-dismissable for CRITICAL severity (no back button).
 * Shows localized template content based on template_id.
 */
class EmergencyBroadcastActivity : AppCompatActivity() {

    private var template: EmergencyBroadcastManager.EmergencyTemplate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        com.securecall.app.security.WindowSecurityHelper.applyFlagSecure(this)

        val templateId = intent.getIntExtra("template_id", -1)
        template = EmergencyBroadcastManager.TEMPLATES[templateId]

        if (template == null) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (template?.dismissable == true) {
                    finish()
                }
            }
        })

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

        // Update button — auto-detects Play Store vs sideload vs ADB-only
        if (t.showUpdateButton) {
            if (com.securecall.app.update.UpdateChecker.isAdbOnlyFlavor()) {
                layout.addView(makeRoundedButton("ADB Update Only", 0xFF888888.toInt()) {
                    android.widget.Toast.makeText(
                        this@EmergencyBroadcastActivity,
                        "Update via ADB only for this build",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                })
            } else {
                layout.addView(makeRoundedButton("Update Now", 0xFF4CAF50.toInt()) {
                    com.securecall.app.update.UpdateManager.openUpdate(this@EmergencyBroadcastActivity)
                })
            }
        }

        // Open URL button (for announcements etc.)
        if (t.openUrl != null) {
            layout.addView(makeRoundedButton("Read Now", 0xFF2196F3.toInt()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.openUrl)))
            })
        }

        // Stealth Delete button
        if (t.showStealthDelete) {
            layout.addView(makeRoundedButton("EMERGENCY DELETE", 0xFFFF0000.toInt()) {
                val prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE)
                prefs.edit().clear().apply()
                val defaultPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@EmergencyBroadcastActivity)
                defaultPrefs.edit().clear().apply()
                for (db in databaseList()) { deleteDatabase(db) }
                cacheDir.deleteRecursively()
                val restartIntent = packageManager.getLaunchIntentForPackage(packageName)
                restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(restartIntent)
                Runtime.getRuntime().exit(0)
            })
        }

        // Dismiss button (only if dismissable)
        if (t.dismissable) {
            layout.addView(makeRoundedButton("I understand", 0xFF555555.toInt()) { finish() })
        } else {
            layout.addView(TextView(this).apply {
                text = if (t.showStealthDelete) "" else "This alert cannot be dismissed."
                textSize = 12f
                setTextColor(0xFF999999.toInt())
            })
        }

        scrollView.addView(layout)
        setContentView(scrollView)
        EdgeToEdgeHelper.applySystemBarPadding(scrollView)
    }

    private fun makeRoundedButton(label: String, color: Int, onClick: () -> Unit): Button {
        val dp = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
            minimumHeight = (48 * dp).toInt()
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = 24 * dp
            }
            background = shape
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = (8 * dp).toInt()
                bottomMargin = (8 * dp).toInt()
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }
}

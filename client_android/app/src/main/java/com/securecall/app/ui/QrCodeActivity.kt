package com.securecall.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.securecall.app.ui.EdgeToEdgeHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Displays a QR code for an invite link.
 * Scan with any camera app to open the invite page.
 */
class QrCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this)
        com.securecall.app.security.WindowSecurityHelper.applyFlagSecure(this)

        val inviteLink = intent.getStringExtra("invite_link") ?: ""
        val contactName = intent.getStringExtra("contact_name") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF0A0A12.toInt())
            setPadding(48, 80, 48, 48)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "\uD83D\uDD12 SecureCall Invite"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        // QR Code
        val qrSize = (280 * resources.displayMetrics.density).toInt()
        val bitmap = generateQrCode(inviteLink, qrSize)
        layout.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            layoutParams = LinearLayout.LayoutParams(qrSize, qrSize).apply {
                gravity = android.view.Gravity.CENTER
                bottomMargin = 32
            }
            setBackgroundColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
        })

        // Instruction
        layout.addView(TextView(this).apply {
            text = if (contactName.isNotEmpty()) "Let $contactName scan this QR code"
                   else "Scan this QR code with any camera app"
            textSize = 16f
            setTextColor(0xFFCCCCCC.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        // SecureID
        layout.addView(TextView(this).apply {
            text = inviteLink
            textSize = 11f
            setTextColor(0xFF666666.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // Close button
        layout.addView(android.widget.Button(this).apply {
            text = "Close"
            textSize = 16f
            setOnClickListener { finish() }
        })

        setContentView(layout)
        EdgeToEdgeHelper.applySystemBarPadding(layout)
    }

    private fun generateQrCode(text: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}

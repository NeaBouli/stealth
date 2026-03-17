package com.securecall.app.data

import android.content.Context
import android.telephony.TelephonyManager

/**
 * FIX 5: Phone number normalization utility.
 * Normalizes various phone number formats to E.164-like international format.
 * +491752536807, 01752536807, 491752536807 all become +491752536807.
 */
object PhoneUtils {

    /**
     * Normalize a phone number for comparison.
     * Strips formatting, converts local to international using device country code.
     */
    fun normalize(phone: String, context: Context? = null): String {
        var num = phone.trim()
            .replace(Regex("[\\s\\-().]+"), "") // strip spaces, dashes, parens, dots

        if (num.isEmpty()) return num

        // Convert 00 prefix to +
        if (num.startsWith("00")) {
            num = "+" + num.substring(2)
        }

        // Convert local format (0xxx) to international using device country code
        if (num.startsWith("0") && !num.startsWith("00") && !num.startsWith("+")) {
            val countryCode = getCountryDialCode(context)
            if (countryCode.isNotEmpty()) {
                num = "+$countryCode${num.substring(1)}"
            }
        }

        // Add + prefix if only digits (e.g., 491752536807 → +491752536807)
        if (num.matches(Regex("^[0-9]+$")) && num.length >= 8) {
            num = "+$num"
        }

        return num
    }

    /**
     * Compare two phone numbers, normalizing both first.
     */
    fun matches(a: String, b: String, context: Context? = null): Boolean {
        val normA = normalize(a, context)
        val normB = normalize(b, context)
        if (normA == normB) return true
        // Also compare stripped (digits only) — last resort
        val digitsA = normA.replace(Regex("[^0-9]"), "")
        val digitsB = normB.replace(Regex("[^0-9]"), "")
        // Match if one ends with the other (handles country code differences)
        return digitsA.length >= 7 && digitsB.length >= 7 &&
            (digitsA.endsWith(digitsB.takeLast(7)) || digitsB.endsWith(digitsA.takeLast(7)))
    }

    private fun getCountryDialCode(context: Context?): String {
        if (context == null) return "49" // Default to Germany
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val iso = tm?.networkCountryIso?.uppercase() ?: tm?.simCountryIso?.uppercase() ?: "DE"
            COUNTRY_DIAL_CODES[iso] ?: "49"
        } catch (_: Exception) { "49" }
    }

    private val COUNTRY_DIAL_CODES = mapOf(
        "DE" to "49", "AT" to "43", "CH" to "41",
        "US" to "1", "GB" to "44", "FR" to "33",
        "IT" to "39", "ES" to "34", "NL" to "31",
        "BE" to "32", "PL" to "48", "CZ" to "420",
        "GR" to "30", "TR" to "90", "RU" to "7",
        "JP" to "81", "CN" to "86", "IN" to "91",
        "AU" to "61", "BR" to "55", "CA" to "1",
        "MX" to "52", "SE" to "46", "NO" to "47",
        "DK" to "45", "FI" to "358", "PT" to "351",
        "IE" to "353", "HU" to "36", "RO" to "40",
        "BG" to "359", "HR" to "385", "SK" to "421"
    )
}

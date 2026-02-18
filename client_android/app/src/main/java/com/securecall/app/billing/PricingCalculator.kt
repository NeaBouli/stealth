package com.securecall.app.billing

/**
 * Dynamic pricing calculator for limited lifetime licenses.
 *
 * PRO:     100 licenses, $15 (first) → $50 (last)
 * PREMIUM: 100 licenses, $25 (first) → $100 (last)
 *
 * Price increases linearly as licenses sell out.
 */
object PricingCalculator {

    // PRO Lifetime
    const val PRO_TOTAL_LICENSES = 100
    const val PRO_MIN_PRICE = 15.0
    const val PRO_MAX_PRICE = 50.0

    // PREMIUM Lifetime
    const val PREMIUM_TOTAL_LICENSES = 100
    const val PREMIUM_MIN_PRICE = 25.0
    const val PREMIUM_MAX_PRICE = 100.0

    fun calculateProPrice(licensesSold: Int): Double {
        val clamped = licensesSold.coerceIn(0, PRO_TOTAL_LICENSES)
        val progress = clamped.toDouble() / PRO_TOTAL_LICENSES
        return PRO_MIN_PRICE + (PRO_MAX_PRICE - PRO_MIN_PRICE) * progress
    }

    fun calculatePremiumPrice(licensesSold: Int): Double {
        val clamped = licensesSold.coerceIn(0, PREMIUM_TOTAL_LICENSES)
        val progress = clamped.toDouble() / PREMIUM_TOTAL_LICENSES
        return PREMIUM_MIN_PRICE + (PREMIUM_MAX_PRICE - PREMIUM_MIN_PRICE) * progress
    }

    fun getRemainingLicenses(tier: String, sold: Int): Int {
        return when (tier) {
            "PRO" -> (PRO_TOTAL_LICENSES - sold).coerceAtLeast(0)
            "PREMIUM" -> (PREMIUM_TOTAL_LICENSES - sold).coerceAtLeast(0)
            else -> 0
        }
    }

    fun formatPrice(price: Double): String {
        return "$%.2f".format(price)
    }
}

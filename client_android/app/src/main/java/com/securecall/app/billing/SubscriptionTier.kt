package com.securecall.app.billing

enum class SubscriptionTier(val displayName: String) {
    FREE("Free"),
    PRO("Pro"),
    PREMIUM("Premium");

    companion object {
        fun fromProductId(productId: String): SubscriptionTier {
            return when {
                productId.contains("premium") -> PREMIUM
                productId.contains("pro") -> PRO
                else -> FREE
            }
        }

        fun fromName(name: String): SubscriptionTier {
            return values().firstOrNull { it.name == name } ?: FREE
        }
    }
}

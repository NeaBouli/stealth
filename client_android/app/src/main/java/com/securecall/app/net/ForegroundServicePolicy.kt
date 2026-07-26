package com.securecall.app.net

/**
 * Central policy for Android 15 (API 35) / 16 foreground-service compliance.
 *
 * On API 35+ the system enforces strict rules on long-running `dataSync`
 * foreground services (timeouts, boot/background start restrictions). SecureCall
 * therefore relies on FCM secure push notifications for incoming calls on those
 * devices and only keeps the foreground service alive while the app is
 * foregrounded or an actual call is active.
 *
 * Pure Kotlin — no Android dependencies — so boundaries are unit-testable on the JVM.
 */
object ForegroundServicePolicy {

    /** Highest API level on which a persistent idle signaling FGS is still allowed. */
    const val PERSISTENT_IDLE_MAX_SDK = 34

    /** True when the persistent idle signaling foreground service may run. */
    fun allowsPersistentIdleSignaling(sdkInt: Int): Boolean =
        sdkInt <= PERSISTENT_IDLE_MAX_SDK

    /** True when starting the signaling FGS after boot is allowed. */
    fun allowsBootStart(sdkInt: Int): Boolean =
        sdkInt <= PERSISTENT_IDLE_MAX_SDK

    /** True when scheduling keep-alive / restart alarms is allowed. */
    fun allowsKeepAlive(sdkInt: Int): Boolean =
        sdkInt <= PERSISTENT_IDLE_MAX_SDK
}

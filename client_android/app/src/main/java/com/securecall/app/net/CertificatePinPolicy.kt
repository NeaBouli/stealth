package com.securecall.app.net

internal object CertificatePinPolicy {
    const val HOST = "api.stealthx.tech"

    /**
     * Overlapping SPKI pins for the current and previous Let's Encrypt chains.
     *
     * Observed current chain (2026-09-19): YR2 -> Root YR.
     * Previous-chain R12 and ISRG Root X1 remain during the migration window.
     * Leaf certificates are intentionally not pinned because their short
     * renewal cycle would add outage risk without improving on the CA pins.
     */
    val PINS = listOf(
        "sha256/nWN7PSep5XDQdge5zK24CnCRXHr3KvzhKEGxsdqCX9E=", // YR2
        "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=", // Root YR
        "sha256/kZwN96eHtZftBWrOZUsd6cA4es80n3NzSk/XtYz2EqQ=", // Previous R12
        "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", // ISRG Root X1
        // ECDSA backups in case the server certificate moves to an ECDSA chain.
        "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w=", // Root YE
        "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI="  // ISRG Root X2
    )

    /**
     * Host/pin pairs for OkHttp. Production pins are always present. An owned-test
     * debug build (Gradle `-PisolatedDeviceTest` plus validated owned-test inputs)
     * adds exactly one pin for its loopback/emulator-gateway host; release builds
     * carry empty owned-test fields and therefore never add an entry.
     */
    fun entries(
        debug: Boolean = com.securecall.app.BuildConfig.DEBUG,
        ownedHost: String = com.securecall.app.BuildConfig.OWNED_TEST_PIN_HOST,
        ownedPin: String = com.securecall.app.BuildConfig.OWNED_TEST_PIN
    ): List<Pair<String, String>> {
        val production = PINS.map { HOST to it }
        if (!debug || ownedHost.isEmpty() || ownedPin.isEmpty()) return production
        require(ownedHost in OWNED_TEST_HOSTS) { "owned-test pin host is not loopback/emulator gateway" }
        require(OWNED_PIN_FORMAT.matches(ownedPin)) { "owned-test pin is not a sha256 SPKI digest" }
        return production + (ownedHost to ownedPin)
    }

    private val OWNED_TEST_HOSTS = setOf("10.0.2.2", "127.0.0.1")
    private val OWNED_PIN_FORMAT = Regex("^sha256/[A-Za-z0-9+/]{43}=$")
}

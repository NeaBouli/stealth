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
        "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // ISRG Root X1
    )
}

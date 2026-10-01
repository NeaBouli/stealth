package com.securecall.app.net

import com.securecall.app.BuildConfig
import com.securecall.app.config.FeatureFlags
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificatePinPolicyTest {
    @Test
    fun `certificate pinning is baseline security in every flavor`() {
        assertTrue(BuildConfig.CERTIFICATE_PINNING)
        assertTrue(FeatureFlags.CERTIFICATE_PINNING)
    }

    @Test
    fun `pin policy covers current and previous lets encrypt hierarchies`() {
        assertEquals("api.stealthx.tech", CertificatePinPolicy.HOST)
        assertEquals(
            setOf(CURRENT_YR2, CURRENT_ROOT_YR, PREVIOUS_R12, ISRG_ROOT_X1),
            CertificatePinPolicy.PINS.toSet()
        )
    }

    @Test
    fun `pin values are unique sha256 spki digests`() {
        val pins = CertificatePinPolicy.PINS

        assertEquals(pins.size, pins.toSet().size)
        assertTrue("at least two non-leaf backups are required", pins.size >= 4)
        pins.forEach { pin ->
            assertTrue("unexpected pin prefix", pin.startsWith("sha256/"))
            assertEquals(32, Base64.getDecoder().decode(pin.removePrefix("sha256/")).size)
        }
    }

    @Test
    fun `certificate pinner builds from the declared policy`() {
        NetworkManager.buildCertificatePinner()
    }

    private companion object {
        const val CURRENT_YR2 = "sha256/nWN7PSep5XDQdge5zK24CnCRXHr3KvzhKEGxsdqCX9E="
        const val CURRENT_ROOT_YR = "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88="
        const val PREVIOUS_R12 = "sha256/kZwN96eHtZftBWrOZUsd6cA4es80n3NzSk/XtYz2EqQ="
        const val ISRG_ROOT_X1 = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="
    }
}

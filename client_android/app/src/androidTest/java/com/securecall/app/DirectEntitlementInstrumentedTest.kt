package com.securecall.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.app.billing.DirectEntitlementStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectEntitlementInstrumentedTest {
    @Test
    fun signedProofRestoresExpiresAndRevokesInAndroidPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("entitlement_instrumentation_fixture", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        var now = 1700000001L
        fun store() = DirectEntitlementStore(prefs, { "android-license-fixture" }, PUBLIC_KEY,
            "pro", "securecall-android-1.0.50-vc78017-api36", { now })
        try {
            assertEquals("FREE", store().currentTier())
            assertTrue(store().accept(TOKEN))
            assertEquals("PRO", store().currentTier())
            assertFalse(store().accept("invalid-proof"))
            assertEquals("PRO", store().currentTier())
            now = 1702592000L
            assertEquals("FREE", store().currentTier())
            assertEquals(TOKEN, store().tokenForRefresh())
            assertTrue(store().revoke())
            assertEquals("FREE", store().currentTier())
            assertEquals(null, store().tokenForRefresh())
        } finally {
            assertTrue(prefs.edit().clear().commit())
        }
    }

    private companion object {
        // Same expired Node-issued fixture as the JVM compatibility test; no retained private key.
        const val PUBLIC_KEY = "OYNKXE-2hnueEC7jVWyQRnKBIcGXGmsY9aUuDB6G4yQ"
        const val TOKEN = "dj0yCmlzcz1zdGVhbHRoeAphdWQ9c2VjdXJlY2FsbApzdWI9YW5kcm9pZC1saWNlbnNlLWZpeHR1cmUKdGllcj1QUk8KcHJvZHVjdD12bGFic19zZWN1cmVjYWxsX3Byb19saWZldGltZQppYXQ9MTcwMDAwMDAwMApleHA9MTcwMjU5MjAwMApvcmRlcj0yOGJkN2U4YWI5MzFmOGNiYjJlODgzMWMzYjYwYjRkMQpjYXRhbG9nPXN0ZWFsdGh4LWxpZmV0aW1lLXYxCm9mZmVyPXNlY3VyZWNhbGwtcHJvLWV1ci0xNTAwLWxpZmV0aW1lLXYxCnJlbGVhc2U9c2VjdXJlY2FsbC1hbmRyb2lkLTEuMC41MC12Yzc4MDE3LWFwaTM2.uBAzdp4RwTrS0eIg8G4dHEyPTfiqC7eTiOOBSFOrbWjLHm1aeSkftWjpcBwTEVWTRJMRY9aVgm4xm_voHfGICg"
    }
}

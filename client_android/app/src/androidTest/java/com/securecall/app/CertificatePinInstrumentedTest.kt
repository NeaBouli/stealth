package com.securecall.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.app.net.CertificatePinPolicy
import com.securecall.app.net.NetworkManager
import okhttp3.CertificatePinner
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CertificatePinInstrumentedTest {
    @Test
    fun productionChainMatchesConfiguredPins() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString("securecallLivePinTest") == "true"
        assumeTrue("live certificate-pin verification is an explicit release gate", enabled)

        val request = Request.Builder()
            .url("https://${CertificatePinPolicy.HOST}/status/live")
            .build()

        NetworkManager.buildPinnedClient(connectTimeoutSec = 20, readTimeoutSec = 20)
            .newCall(request)
            .execute()
            .use { response ->
                assertTrue("status endpoint must be reachable", response.isSuccessful)
                val peerPins = response.handshake
                    ?.peerCertificates
                    ?.map(CertificatePinner::pin)
                    .orEmpty()
                assertTrue(
                    "cleaned Android chain must contain a configured SPKI pin",
                    peerPins.any(CertificatePinPolicy.PINS::contains)
                )
            }
    }
}

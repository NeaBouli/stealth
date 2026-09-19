package com.securecall.app.net

import com.securecall.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito

class WebSocketServiceActivationGateTest {
    @Test
    fun disabledFreeActivationReturnsBeforeRequestSlotOrNetworkSend() {
        assertFalse(BuildConfig.ACTIVATION_CODE_ENABLED)
        val service = Mockito.mock(WebSocketService::class.java, Answers.CALLS_REAL_METHODS)
        val results = mutableListOf<Triple<Boolean, String, String>>()

        repeat(2) {
            service.activateCode("DIRECT-PRO-TEST") { success, tier, error ->
                results += Triple(success, tier, error)
            }
        }

        assertEquals(
            listOf(
                Triple(false, "", "activation_disabled"),
                Triple(false, "", "activation_disabled")
            ),
            results
        )
        val invokedMethods = Mockito.mockingDetails(service).invocations.map { it.method.name }
        assertFalse(invokedMethods.any { it.startsWith("beginActivationRequest") })
        assertFalse(invokedMethods.any { it.startsWith("sendActivationMessage") })
    }
}

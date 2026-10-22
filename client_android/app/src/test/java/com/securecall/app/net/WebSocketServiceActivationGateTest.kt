package com.securecall.app.net

import com.securecall.app.BuildConfig
import com.securecall.app.R
import com.securecall.app.billing.ActivationRequestSlot
import com.securecall.app.ui.activationErrorMessageResource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito

class WebSocketServiceActivationGateTest {
    @Test
    fun activationFlagMatchesDistribution() {
        assertEquals(BuildConfig.FLAVOR in setOf("pro", "premium"), BuildConfig.ACTIVATION_CODE_ENABLED)
    }

    @Test
    fun disabledFreeActivationReturnsBeforeRequestSlotOrNetworkSend() {
        assumeTrue(BuildConfig.FLAVOR == "free")
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

    @Test
    fun directActivationReachesTransportAndCanRetryAfterSendFailure() {
        assumeTrue(BuildConfig.FLAVOR in setOf("pro", "premium"))
        val service = Mockito.mock(WebSocketService::class.java, Answers.CALLS_REAL_METHODS)
        val requests = ActivationRequestSlot()
        WebSocketService::class.java.getDeclaredField("activationRequests").apply {
            isAccessible = true
            set(service, requests)
        }
        val results = mutableListOf<Triple<Boolean, String, String>>()

        repeat(2) {
            service.activateCode(" direct-pro-test ") { success, tier, error ->
                results += Triple(success, tier, error)
            }
        }

        assertEquals(List(2) { Triple(false, "", "not_connected") }, results)
        assertNull(requests.clear())
        val messages = Mockito.mockingDetails(service).invocations
            .filter { it.method.name.startsWith("sendActivationMessage") }
            .map { JSONObject(it.arguments.single() as String) }
        assertEquals(2, messages.size)
        messages.forEach {
            assertEquals("ACTIVATE_CODE", it.getString("type"))
            assertEquals("DIRECT-PRO-TEST", it.getString("code"))
            assertEquals(BuildConfig.FLAVOR, it.getString("edition"))
            assertEquals(BuildConfig.ENTITLEMENT_RELEASE_ID, it.getString("release"))
        }
        assertNotEquals(messages[0].getString("requestId"), messages[1].getString("requestId"))
    }

    @Test
    fun activationPreflightAndRateLimitErrorsHaveUserMessages() {
        assertEquals(R.string.activation_error_edition_mismatch, activationErrorMessageResource("edition_mismatch"))
        assertEquals(R.string.activation_error_release_mismatch, activationErrorMessageResource("release_mismatch"))
        assertEquals(R.string.activation_error_code_unsupported, activationErrorMessageResource("code_unsupported"))
        assertEquals(R.string.activation_error_rate_limited, activationErrorMessageResource("rate_limited"))
        assertEquals(R.string.activation_error_connection, activationErrorMessageResource("timeout"))
        assertNull(activationErrorMessageResource("unknown_error"))
    }
}

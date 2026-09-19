package com.securecall.app.billing

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TesterLicenseClientTest {
    private class Harness {
        val sent = mutableListOf<JSONObject>()
        val results = mutableListOf<Pair<Boolean, String>>()
        val timers = mutableListOf<() -> Unit>()
        var device: String? = "a".repeat(64)
        var identity: String? = "synthetic"
        var connected = true
        var valid = true
        var signed = 0
        val client = TesterLicenseClient(
            send = { if (connected) sent.add(it); connected }, keyHash = { device }, subject = { identity },
            sign = { signed++; "synthetic-signature" }, accept = { valid && it == "synthetic-proof" },
            schedule = { _, action -> timers.add(action) }
        )
        fun start(renewal: Boolean = false) = client.start("synthetic-input", renewal) { ok, error -> results.add(ok to error) }
        fun challenge(renewal: Boolean = false): JSONObject {
            val id = "12345678-1234-1234-1234-123456789012"
            val domain = if (renewal) "renewal" else "activation"
            return JSONObject().put("type", "TESTER_${if (renewal) "RENEWAL" else "ACTIVATION"}_CHALLENGE")
                .put("requestId", sent[0].getString("requestId")).put("success", true).put("challengeId", id)
                .put("challenge", "securecall-tester-$domain-v1\n$id\n${"b".repeat(64)}\nsynthetic\n${"a".repeat(64)}\n${"n".repeat(43)}")
        }
        fun result(renewal: Boolean = false) = JSONObject().put("type", "TESTER_${if (renewal) "RENEWAL" else "ACTIVATION"}_RESULT")
            .put("requestId", sent[0].getString("requestId")).put("success", true).put("entitlementToken", "synthetic-proof")
    }

    @Test fun activationAndRenewalCompleteOnlyAfterProofValidation() {
        for (renewal in listOf(false, true)) {
            val h = Harness(); h.start(renewal)
            h.client.receive(h.result(renewal)); assertTrue(h.results.isEmpty())
            h.client.receive(h.challenge(renewal)); assertEquals(1, h.signed)
            h.client.receive(h.challenge(renewal)); assertEquals(1, h.signed)
            h.client.receive(h.result(renewal)); assertEquals(listOf(true to ""), h.results)
            h.client.receive(h.result(renewal)); h.timers[0]()
            assertEquals(1, h.results.size)
        }
    }

    @Test fun correlationIdentityDomainAndInvalidProofFailClosed() {
        val stale = Harness(); stale.start()
        stale.client.receive(stale.challenge().put("requestId", "other")); assertEquals(0, stale.signed)
        val domain = Harness(); domain.start()
        domain.client.receive(domain.challenge().put("challenge", "unrelated signing request"))
        assertEquals(0, domain.signed); assertFalse(domain.results.single().first)
        val identity = Harness(); identity.start(); identity.identity = "changed"
        identity.client.receive(identity.challenge()); assertEquals(0, identity.signed)
        val proof = Harness(); proof.start(); proof.valid = false
        proof.client.receive(proof.challenge()); proof.client.receive(proof.result())
        assertEquals(false to "entitlement_invalid", proof.results.single())
    }

    @Test fun missingKeyDisconnectTimeoutAndConcurrentRequest() {
        val missing = Harness(); missing.device = null; missing.start(); assertTrue(missing.sent.isEmpty())
        val offline = Harness(); offline.connected = false; offline.start(); assertEquals(false to "not_connected", offline.results.single())
        val timeout = Harness(); timeout.start(); timeout.timers[0](); assertEquals(false to "timeout", timeout.results.single())
        val cancel = Harness(); cancel.start(); cancel.client.cancel(); cancel.client.receive(cancel.challenge())
        assertEquals(0, cancel.signed)
        val busy = Harness(); busy.start(); busy.start(); assertEquals(false to "activation_in_progress", busy.results.single())
        assertEquals(1, busy.sent.size)
    }
}

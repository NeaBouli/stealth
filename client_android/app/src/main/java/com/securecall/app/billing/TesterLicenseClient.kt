package com.securecall.app.billing

import org.json.JSONObject
import java.util.UUID

/** One challenge exchange at a time; no code retained after the initial send. */
class TesterLicenseClient(
    private val send: (JSONObject) -> Boolean,
    private val deviceIdentity: (createIfMissing: Boolean) -> TesterDeviceIdentity?,
    private val subject: () -> String?,
    private val sign: (String) -> String?,
    private val accept: (String) -> Boolean,
    private val schedule: (Long, () -> Unit) -> Unit
) {
    private data class Pending(val id: String, val renewal: Boolean, val identity: String,
        val device: String, val callback: (Boolean, String) -> Unit, var completing: Boolean = false)
    private var pending: Pending? = null

    @Synchronized
    fun start(value: String, renewal: Boolean, callback: (Boolean, String) -> Unit) {
        if (pending != null) { callback(false, "activation_in_progress"); return }
        val identity = subject()
        if (identity.isNullOrEmpty()) { callback(false, "tester_enrollment_required"); return }
        val device = deviceIdentity(!renewal)
        if (device == null) { callback(false, "tester_enrollment_required"); return }
        val request = Pending(UUID.randomUUID().toString(), renewal, identity, device.keyHash, callback)
        pending = request
        val message = JSONObject().put("type", if (renewal) "TESTER_RENEWAL_BEGIN" else "TESTER_ACTIVATION_BEGIN")
            .put("requestId", request.id)
        if (renewal) message.put("entitlementToken", value).put("keyHash", device.keyHash)
        else message.put("code", value).put("packageName", "com.securecall.app.premium")
            .put("publicKey", device.publicKey)
        if (!send(message)) { finish(false, "not_connected"); return }
        schedule(15_000) { timeout(request.id) }
    }

    @Synchronized
    private fun timeout(id: String) { if (pending?.id == id) finish(false, "timeout") }

    @Synchronized
    fun cancel() { if (pending != null) finish(false, "not_connected") }

    @Synchronized
    fun receive(message: JSONObject): Boolean {
        val type = message.optString("type")
        if (type !in RESPONSE_TYPES) return false
        val request = pending ?: return true
        if (message.optString("requestId") != request.id) return true
        val prefix = if (request.renewal) "TESTER_RENEWAL" else "TESTER_ACTIVATION"
        val expected = prefix + if (request.completing) "_RESULT" else "_CHALLENGE"
        if (type != expected) return true
        if (subject() != request.identity || deviceIdentity(false)?.keyHash != request.device) {
            finish(false, "tester_identity_changed")
            return true
        }
        if (!message.optBoolean("success", false)) { finish(false, "tester_license_unavailable"); return true }
        if (request.completing) {
            val valid = accept(message.optString("entitlementToken"))
            finish(valid, if (valid) "" else "entitlement_invalid")
            return true
        }
        val challenge = message.optString("challenge")
        val id = message.optString("challengeId")
        val parts = challenge.split('\n')
        val domain = if (request.renewal) "securecall-tester-renewal-v1" else "securecall-tester-activation-v1"
        if (parts.size != 6 || parts[0] != domain || parts[1] != id || !UUID_PATTERN.matches(id) ||
            !HASH_PATTERN.matches(parts[2]) || parts[3] != request.identity || parts[4] != request.device ||
            !NONCE_PATTERN.matches(parts[5])) { finish(false, "invalid_tester_challenge"); return true }
        val signature = sign(challenge)
        if (signature == null) { finish(false, "tester_key_unavailable"); return true }
        request.completing = true
        if (!send(JSONObject().put("type", prefix + "_COMPLETE").put("requestId", request.id)
                .put("challengeId", id).put("signature", signature))) finish(false, "not_connected")
        return true
    }

    private fun finish(success: Boolean, error: String) {
        val request = pending ?: return
        pending = null
        request.callback(success, error)
    }

    companion object {
        private val UUID_PATTERN = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        private val HASH_PATTERN = Regex("[a-f0-9]{64}")
        private val NONCE_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        private val RESPONSE_TYPES = setOf("TESTER_ACTIVATION_CHALLENGE", "TESTER_ACTIVATION_RESULT",
            "TESTER_RENEWAL_CHALLENGE", "TESTER_RENEWAL_RESULT")
    }
}

package com.securecall.app.data

import org.json.JSONObject
import java.util.UUID

enum class CallType { INCOMING, OUTGOING, MISSED }

data class CallRecord(
    val id: String = UUID.randomUUID().toString(),
    val contactName: String,
    val contactId: String? = null,
    val type: CallType,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val encrypted: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("contactName", contactName)
        put("contactId", contactId ?: "")
        put("type", type.name)
        put("timestamp", timestamp)
        put("durationSeconds", durationSeconds)
        put("encrypted", encrypted)
    }

    companion object {
        fun fromJson(json: JSONObject): CallRecord = CallRecord(
            id = json.getString("id"),
            contactName = json.getString("contactName"),
            contactId = json.optString("contactId", "").ifEmpty { null },
            type = CallType.valueOf(json.getString("type")),
            timestamp = json.getLong("timestamp"),
            durationSeconds = json.optInt("durationSeconds", 0),
            encrypted = json.optBoolean("encrypted", true)
        )
    }
}

package com.securecall.app.data

import org.json.JSONObject
import java.util.UUID

data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneOrId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isPhoneContact: Boolean = false,
    val secureId: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phoneOrId", phoneOrId)
        put("createdAt", createdAt)
        if (secureId != null) put("secureId", secureId)
    }

    companion object {
        fun fromJson(json: JSONObject): Contact = Contact(
            id = json.getString("id"),
            name = json.getString("name"),
            phoneOrId = json.getString("phoneOrId"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            secureId = json.optString("secureId", "").let { if (it.isEmpty()) null else it }
        )
    }
}

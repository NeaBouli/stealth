package com.securecall.app.data

import org.json.JSONObject
import java.util.UUID

data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneOrId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isPhoneContact: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phoneOrId", phoneOrId)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Contact = Contact(
            id = json.getString("id"),
            name = json.getString("name"),
            phoneOrId = json.getString("phoneOrId"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

package com.securecall.app.net.signal

import org.json.JSONObject
import java.util.UUID

/**
 * PATCH 202:
 * Minimale Call-Signaling-Struktur (CALL_INIT / CALL_BYE).
 *
 * Später:
 *  - CALL_ACCEPT / REJECT
 *  - Re-INVITE, Reconnect, etc.
 */

data class CallInit(
    val callId: String = UUID.randomUUID().toString(),
    val role: String = "caller"
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", "call-init")
        obj.put("callId", callId)
        obj.put("role", role)
        return obj.toString()
    }
}

data class CallBye(
    val callId: String
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", "call-bye")
        obj.put("callId", callId)
        return obj.toString()
    }
}

object CallSignalParser {
    fun parse(json: String): Pair<String, String>? {
        return try {
            val obj = JSONObject(json)
            val type = obj.getString("type")
            if (type == "call-init" || type == "call-bye") {
                val callId = obj.getString("callId")
                Pair(type, callId)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }
}

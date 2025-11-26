package com.securecall.app.net.signal

import org.json.JSONObject

/**
 * PATCH 201:
 * Kleine JSON-Wrapper für Public-Key-Austausch.
 */

data class KeyOffer(val pubKey: ByteArray) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", "key-offer")
        obj.put("pub", android.util.Base64.encodeToString(pubKey, android.util.Base64.NO_WRAP))
        return obj.toString()
    }
}

data class KeyAnswer(val pubKey: ByteArray) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", "key-answer")
        obj.put("pub", android.util.Base64.encodeToString(pubKey, android.util.Base64.NO_WRAP))
        return obj.toString()
    }
}

object SignalParser {
    fun parse(json: String): Pair<String, ByteArray>? {
        return try {
            val obj = JSONObject(json)
            val type = obj.getString("type")
            val pubB64 = obj.getString("pub")
            val pub = android.util.Base64.decode(pubB64, android.util.Base64.NO_WRAP)
            Pair(type, pub)
        } catch (e: Throwable) {
            null
        }
    }
}

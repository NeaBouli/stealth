package com.securecall.app.session

/**
 * BACKEND-22: SessionManager
 *
 * Speichert die aktuelle Call-Session (MVP).
 */

object SessionManager {
    var currentSessionId: String? = null

    fun setSession(id: String) {
        currentSessionId = id
    }

    fun clear() {
        currentSessionId = null
    }
}

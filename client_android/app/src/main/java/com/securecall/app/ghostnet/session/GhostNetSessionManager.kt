package com.securecall.app.ghostnet.session

import android.util.Log
import java.util.UUID

/**
 * PATCH 233:
 * Zentrale Verwaltung einer (vorerst einzelnen) GhostNet-Session.
 * Später Multi-Session-Support möglich.
 */
object GhostNetSessionManager {

    private var current: GhostNetSession? = null

    // PATCH 236: session listeners
    private val listeners = mutableListOf<SessionListener>()

    fun createNewSession(): GhostNetSession {
        val id = UUID.randomUUID().toString()
        val session = GhostNetSession(id)
        current = session
        Log.d("GHOST_SESSION", "Created new session with id=$id")
        return session
    }

    fun get(): GhostNetSession {
        if (current == null) {
            current = createNewSession()
        }
        return current!!
    }

    fun endSession() {
        current?.setState(GhostNetSessionState.DEAD)
        Log.d("GHOST_SESSION", "Session ended")
        current = null
    }

    fun addListener(l: SessionListener) {
        listeners.add(l)
    }

    fun removeListener(l: SessionListener) {
        listeners.remove(l)
    }

    fun notifyListeners(st: GhostNetSessionState) {
        for (l in listeners) l.onSessionStateChanged(st)
    }

    // PATCH 237: session reset API
    fun resetSession() {
        Log.w("SESSION", "resetSession() invoked")
        val idle = GhostNetSessionState.IDLE
        get().setState(idle)
        notifyListeners(idle)
    }
}

package com.securecall.app.ghostnet.session

interface SessionListener {
    fun onSessionStateChanged(newState: GhostNetSessionState)
}

package com.securecall.app.ghostnet.call

interface CallStateListener {
    fun onCallStateChanged(newState: GhostCallState)
}

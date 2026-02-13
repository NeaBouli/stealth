package com.securecall.app.call

import android.util.Log

/**
 * PATCH 203 / 205:
 * Globaler Manager für Call-Lifecycle.
 */
object CallController {

    private const val TAG = "CALL_CTRL"

    private var state: CallState = CallState.IDLE
    private var activeCallId: String? = null

    fun getState(): CallState = state
    fun getCallId(): String? = activeCallId

    fun reset() {
        Log.d(TAG, "reset() → IDLE")
        state = CallState.IDLE
        activeCallId = null
    }

    fun incomingCall(callId: String) {
        Log.d(TAG, "incomingCall(): callId=$callId")
        state = CallState.RINGING
        activeCallId = callId
    }

    fun outgoingCall(callId: String) {
        Log.d(TAG, "outgoingCall(): callId=$callId")
        state = CallState.OUTGOING
        activeCallId = callId
    }

    fun acceptCall() {
        if (state == CallState.RINGING || state == CallState.OUTGOING) {
            Log.d(TAG, "acceptCall() → ACTIVE")
            state = CallState.ACTIVE
            notifySessionActive()
        } else {
            Log.w(TAG, "acceptCall(): invalid state=$state")
        }
    }

    fun endCall() {
        Log.d(TAG, "endCall(): → ENDED")
        state = CallState.ENDED
        notifySessionEnded()
    }

    // PATCH 205: Session-Lifecycle anstoßen
    private fun notifySessionActive() {
        try {
            com.securecall.app.ghostnet.session.GhostNetSession.get().onCallActive()
        } catch (t: Throwable) {
            Log.e(TAG, "notifySessionActive() failed", t)
        }
    }

    private fun notifySessionEnded() {
        try {
            com.securecall.app.ghostnet.session.GhostNetSession.get().onCallEnded()
        } catch (t: Throwable) {
            Log.e(TAG, "notifySessionEnded() failed", t)
        }
    }
}

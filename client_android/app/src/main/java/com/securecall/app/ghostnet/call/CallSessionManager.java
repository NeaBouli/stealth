package com.securecall.app.ghostnet.call;

import android.util.Log;

public class CallSessionManager {

    /**
     * Reason why a call/session ended. This is mainly for logging/analytics
     * and for future UI integration by the Stealth developer.
     */
    public enum CallEndReason {
        NORMAL_HANGUP,   // User pressed hang-up / CONTROL_BYE
        NETWORK_ERROR,   // onFailure() due to network issues
        PROTOCOL_ERROR,  // malformed data / unexpected protocol state
        REMOTE_CLOSED,   // remote peer/server closed the connection
        UNKNOWN          // fallback / unspecified reason
    }

    // Last known reason why the call ended (for logging/analytics)
    private CallEndReason lastEndReason = CallEndReason.UNKNOWN;




    private static final String TAG = "GHOSTNET_WS";

    public enum CallState {
        IDLE,
        IN_CALL
    }

    private static CallSessionManager INSTANCE;

    private CallState state = CallState.IDLE;

    private CallSessionManager() {
        Log.d(TAG, "CallSessionManager(): created, initial state=" + state);
    }

    public static synchronized CallSessionManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CallSessionManager();
        }
        return INSTANCE;
    }

    public synchronized CallState getState() {
        return state;
    }

    public synchronized void updateState(CallState newState) {
        Log.d(TAG, "CallSessionManager.updateState(): " + state + " -> " + newState);
        state = newState;
    }

    public synchronized void onWebSocketConnected() {
        Log.d(TAG, "CallSessionManager.onWebSocketConnected(): current=" + state);
        updateState(CallState.IN_CALL);
    }

    public synchronized void onWebSocketClosed(int code, String reason) {
        Log.d(TAG, "onWebSocketClosed(): code=" + code + " reason=" + reason + " state=" + state);

        if (code == 1000) {
            // Normaler, sauberer Hangup (Client oder Remote) -> als NORMAL_HANGUP klassifizieren
            lastEndReason = CallEndReason.NORMAL_HANGUP;
        } else {
            // Alle anderen Close-Codes erst einmal als REMOTE_CLOSED behandeln
            lastEndReason = CallEndReason.REMOTE_CLOSED;
        }

        Log.d(TAG, "onWebSocketClosed(): lastEndReason=" + lastEndReason);
        updateState(CallState.IDLE);
    }



    public synchronized void onWebSocketError(Throwable t) {
        Log.d(TAG, "onWebSocketError(): " + t);
        lastEndReason = CallEndReason.NETWORK_ERROR;
        Log.d(TAG, "onWebSocketError(): lastEndReason=" + lastEndReason);
        updateState(CallState.IDLE);
    }


    public String getStateName() {
        return state != null ? state.name() : "UNKNOWN";
    }


    public CallEndReason getLastEndReason() {
        return lastEndReason;
    }

}

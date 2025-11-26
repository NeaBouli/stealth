package com.securecall.app.ghostnet;

import java.util.UUID;

public class GhostNetSession {

    private final String sessionId;
    private String remotePeerId;
    private boolean active;

    public GhostNetSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.remotePeerId = null;
        this.active = false;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRemotePeerId() {
        return remotePeerId;
    }

    public void setRemotePeerId(String remotePeerId) {
        this.remotePeerId = remotePeerId;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}

    // BACKEND-24: sichere Getter für Session-Infos
    public static synchronized String getSafeSessionId() {
        return (sessionId != null ? sessionId : "NO_SESSION");
    }

    public static synchronized java.util.List<GhostNetRelayHint> getSafeRelayHints() {
        return (relayHints != null ? relayHints : java.util.Collections.emptyList());
    }

    public static synchronized String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("SESSION = ").append(getSafeSessionId()).append(" | RELAYS = ");
        if (relayHints == null || relayHints.isEmpty()) {
            sb.append("NONE");
        } else {
            for (GhostNetRelayHint h : relayHints) {
                sb.append(h.getHost()).append(":").append(h.getPort()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    // BACKEND-25: Session-Reset (vollständig)
    public static synchronized void resetSession() {
        sessionId = null;
        relayHints = null;
        lastHandshakeTs = 0;
    }

    // BACKEND-25: Soft-Reset (z.B. bei UI-Wechsel)
    public static synchronized void softReset() {
        lastHandshakeTs = 0;
    }

    // BACKEND-27: Session-Setter für Tests & Pre-Handshake
    public static synchronized void setSessionData(String id, java.util.List<GhostNetRelayHint> relays) {
        sessionId = id;
        relayHints = relays;
        lastHandshakeTs = System.currentTimeMillis();
    }

    // BACKEND-28: Relay-Validierung beim Setzen
    public static synchronized void setValidatedSessionData(
            String id,
            java.util.List<GhostNetRelayHint> relays
    ) {
        sessionId = id;

        java.util.List<GhostNetRelayHint> validList = new java.util.ArrayList<>();
        if (relays != null) {
            for (GhostNetRelayHint h : relays) {
                if (h != null && h.isValid()) {
                    validList.add(h);
                } else {
                    android.util.Log.w("GHOST_VALIDATION",
                        "Ungültiger RelayHint verworfen: " + h);
                }
            }
        }

        relayHints = validList;
        lastHandshakeTs = System.currentTimeMillis();

        android.util.Log.d("GHOST_VALIDATION",
            "Session validated: session=" + sessionId +
            " relays=" + relayHints.size());
    }

    // BACKEND-28: Relay-Validierung beim Setzen
    public static synchronized void setValidatedSessionData(
            String id,
            java.util.List<GhostNetRelayHint> relays
    ) {
        sessionId = id;

        java.util.List<GhostNetRelayHint> validList = new java.util.ArrayList<>();
        if (relays != null) {
            for (GhostNetRelayHint h : relays) {
                if (h != null && h.isValid()) {
                    validList.add(h);
                } else {
                    android.util.Log.w("GHOST_VALIDATION",
                        "Ungültiger RelayHint verworfen: " + h);
                }
            }
        }

        relayHints = validList;
        lastHandshakeTs = System.currentTimeMillis();

        android.util.Log.d("GHOST_VALIDATION",
            "Session validated: session=" + sessionId +
            " relays=" + relayHints.size());
    }

    // BACKEND-29: GhostNet Session State Machine
    public enum State {
        INIT,       // App gestartet, nichts geladen
        PREPARED,   // Pre-Handshake erfolgreich (SESSION-ID + relays)
        ACTIVE,     // Transport läuft (Android-03/04)
        DEAD        // Disconnect / Fehler / Reset
    }

    private static State sessionState = State.INIT;

    public static synchronized State getState() {
        return sessionState;
    }

    private static synchronized void setState(State s) {
        sessionState = s;
        android.util.Log.d("GHOST_STATE", "GhostNet state changed to: " + s);
    }

    // BACKEND-29: State-Update nach Pre-Handshake
    public static synchronized void markPrepared() {
        setState(State.PREPARED);
    }

    // BACKEND-29: State-Update nach Transport-Aktivierung
    public static synchronized void markActive() {
        setState(State.ACTIVE);
    }

    // BACKEND-29: State-Update für Fehler / Reset
    public static synchronized void markDead() {
        setState(State.DEAD);
    }

    // BACKEND-29: Reset muss auch den State updaten
    public static synchronized void resetSessionWithState() {
        resetSession();
        setState(State.DEAD);
    }

    // BACKEND-31: Observer für State-Änderungen
    public interface StateListener {
        void onStateChanged(State newState);
    }

    private static final java.util.List<StateListener> stateListeners = new java.util.ArrayList<>();

    public static synchronized void addStateListener(StateListener l) {
        if (l != null && !stateListeners.contains(l)) {
            stateListeners.add(l);
        }
    }

    public static synchronized void removeStateListener(StateListener l) {
        stateListeners.remove(l);
    }

    private static synchronized void notifyStateListeners(State s) {
        for (StateListener l : stateListeners) {
            try {
                l.onStateChanged(s);
            } catch (Exception e) {
                android.util.Log.e("GHOST_STATE", "Listener exception: " + e.getMessage());
            }
        }
    }

    // BACKEND-31: Listener-Trigger ergänzen
    private static synchronized void setState(State s) {
        sessionState = s;
        android.util.Log.d("GHOST_STATE", "GhostNet state changed to: " + s);
        notifyStateListeners(s);
    }

    // BACKEND-32: Lifecycle-Events für Session
    public interface LifecycleListener {
        void onPrepared();
        void onActivated();
        void onDead();
    }

    private static final java.util.List<LifecycleListener> lifecycleListeners =
            new java.util.ArrayList<>();

    public static synchronized void addLifecycleListener(LifecycleListener l) {
        if (l != null && !lifecycleListeners.contains(l)) {
            lifecycleListeners.add(l);
        }
    }

    public static synchronized void removeLifecycleListener(LifecycleListener l) {
        lifecycleListeners.remove(l);
    }

    private static void notifyPrepared() {
        for (LifecycleListener l : lifecycleListeners) {
            try { l.onPrepared(); } catch (Exception e) {}
        }
    }

    private static void notifyActivated() {
        for (LifecycleListener l : lifecycleListeners) {
            try { l.onActivated(); } catch (Exception e) {}
        }
    }

    private static void notifyDead() {
        for (LifecycleListener l : lifecycleListeners) {
            try { l.onDead(); } catch (Exception e) {}
        }
    }

    // BACKEND-32: Lifecycle Trigger
    public static synchronized void markPrepared() {
        setState(State.PREPARED);
        notifyPrepared();
    }

    public static synchronized void markActive() {
        setState(State.ACTIVE);
        notifyActivated();
    }

    public static synchronized void markDead() {
        setState(State.DEAD);
        notifyDead();
    }

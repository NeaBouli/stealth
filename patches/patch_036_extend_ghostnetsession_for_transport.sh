#!/bin/bash
set -e

echo "== patch_036: extend GhostNetSession for GhostNetTransport expectations =="

cat <<'JAVA' > client_android/app/src/main/java/com/securecall/app/ghostnet/GhostNetSession.java
package com.securecall.app.ghostnet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal session model and state machine stub for GhostNet.
 *
 * This keeps a simple static session model, but also provides instance
 * helpers that GhostNetTransport can call (getSessionId(), activate(), etc.).
 */
public class GhostNetSession {

    // Static session data
    private static String sessionId;
    private static String remotePeerId;
    private static List<GhostNetRelayHint> relayHints;
    private static long lastHandshakeTs;

    // ---------- Static API ----------

    public static synchronized String getSafeSessionId() {
        return sessionId != null ? sessionId : "";
    }

    public static synchronized List<GhostNetRelayHint> getSafeRelayHints() {
        if (relayHints == null) return Collections.emptyList();
        return new ArrayList<>(relayHints);
    }

    public static synchronized String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("SESSION = ").append(getSafeSessionId()).append(" | RELAYS = ");
        if (relayHints == null || relayHints.isEmpty()) {
            sb.append("none");
        } else {
            for (GhostNetRelayHint h : relayHints) {
                sb.append(h.asCompactString()).append(" ");
            }
        }
        return sb.toString();
    }

    public static synchronized void resetSession() {
        sessionId = null;
        remotePeerId = null;
        relayHints = null;
        lastHandshakeTs = 0L;
        sessionState = State.INIT;
    }

    public static synchronized void softReset() {
        sessionId = null;
        remotePeerId = null;
        relayHints = null;
    }

    public static synchronized void setSessionData(String id, List<GhostNetRelayHint> relays) {
        sessionId = id;
        relayHints = relays != null ? new ArrayList<>(relays) : null;
        lastHandshakeTs = System.currentTimeMillis();
    }

    public static synchronized void setValidatedSessionData(String id, List<GhostNetRelayHint> relays) {
        setSessionData(id, relays);
    }

    // Extra static helpers used by transport-like code
    public static synchronized void setRemotePeerIdStatic(String peerId) {
        remotePeerId = peerId;
    }

    public static synchronized String getRemotePeerIdStatic() {
        return remotePeerId;
    }

    // ---------- State machine ----------

    public enum State {
        INIT,
        PREPARED,
        ACTIVE,
        DEAD
    }

    private static State sessionState = State.INIT;

    public static synchronized State getState() {
        return sessionState;
    }

    public interface StateListener {
        void onStateChanged(State s);
    }

    public interface LifecycleListener {
        void onPrepared();
        void onActivated();
        void onDead();
    }

    private static final List<StateListener> stateListeners = new ArrayList<>();
    private static final List<LifecycleListener> lifecycleListeners = new ArrayList<>();

    public static synchronized void addStateListener(StateListener l) {
        if (l != null && !stateListeners.contains(l)) {
            stateListeners.add(l);
        }
    }

    public static synchronized void removeStateListener(StateListener l) {
        stateListeners.remove(l);
    }

    private static void notifyStateListeners(State s) {
        for (StateListener l : new ArrayList<>(stateListeners)) {
            try {
                l.onStateChanged(s);
            } catch (Exception ignored) {
            }
        }
    }

    public static synchronized void addLifecycleListener(LifecycleListener l) {
        if (l != null && !lifecycleListeners.contains(l)) {
            lifecycleListeners.add(l);
        }
    }

    public static synchronized void removeLifecycleListener(LifecycleListener l) {
        lifecycleListeners.remove(l);
    }

    private static void notifyPrepared() {
        for (LifecycleListener l : new ArrayList<>(lifecycleListeners)) {
            try { l.onPrepared(); } catch (Exception ignored) {}
        }
    }

    private static void notifyActivated() {
        for (LifecycleListener l : new ArrayList<>(lifecycleListeners)) {
            try { l.onActivated(); } catch (Exception ignored) {}
        }
    }

    private static void notifyDead() {
        for (LifecycleListener l : new ArrayList<>(lifecycleListeners)) {
            try { l.onDead(); } catch (Exception ignored) {}
        }
    }

    public static synchronized void markPrepared() {
        sessionState = State.PREPARED;
        notifyPrepared();
        notifyStateListeners(sessionState);
    }

    public static synchronized void markActive() {
        sessionState = State.ACTIVE;
        notifyActivated();
        notifyStateListeners(sessionState);
    }

    public static synchronized void markDead() {
        sessionState = State.DEAD;
        notifyDead();
        notifyStateListeners(sessionState);
    }

    public static synchronized void resetSessionWithState() {
        resetSession();
        sessionState = State.DEAD;
        notifyDead();
        notifyStateListeners(sessionState);
    }

    // ---------- Instance helpers for GhostNetTransport ----------

    /**
     * Transport code often uses an instance of GhostNetSession.
     * These instance methods simply delegate to the static state above.
     */

    public String getSessionId() {
        return getSafeSessionId();
    }

    public void activate() {
        markActive();
    }

    public void deactivate() {
        markDead();
    }

    public boolean isActive() {
        return getState() == State.ACTIVE;
    }

    public void setRemotePeerId(String peerId) {
        setRemotePeerIdStatic(peerId);
    }

    public String getRemotePeerId() {
        return getRemotePeerIdStatic();
    }
}
JAVA

echo "[OK] Rewrote GhostNetSession.java with transport-compatible stubs"
echo "== patch_036 done =="

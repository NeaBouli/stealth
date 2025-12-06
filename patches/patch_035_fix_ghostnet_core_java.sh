#!/bin/bash
set -e

echo "== patch_035: rewrite GhostNet core Java classes as clean stubs =="

# 1) GhostNetRelayHint.java
cat <<'JAVA' > client_android/app/src/main/java/com/securecall/app/ghostnet/GhostNetRelayHint.java
package com.securecall.app.ghostnet;

/**
 * Minimal relay hint value object for GhostNet.
 */
public class GhostNetRelayHint {

    public final String host;
    public final int port;

    public GhostNetRelayHint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean isValid() {
        if (host == null || host.isEmpty()) return false;
        if (port <= 0 || port > 65535) return false;
        return true;
    }

    public String asCompactString() {
        return host + ":" + port;
    }

    @Override
    public String toString() {
        return "GhostNetRelayHint(" + asCompactString() + ")";
    }
}
JAVA

echo "[OK] Wrote GhostNetRelayHint.java"

# 2) GhostNetSession.java
cat <<'JAVA' > client_android/app/src/main/java/com/securecall/app/ghostnet/GhostNetSession.java
package com.securecall.app.ghostnet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal session model and state machine stub for GhostNet.
 *
 * This is intentionally simple but keeps the public surface usable.
 */
public class GhostNetSession {

    private static String sessionId;
    private static List<GhostNetRelayHint> relayHints;
    private static long lastHandshakeTs;

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
        relayHints = null;
        lastHandshakeTs = 0L;
        sessionState = State.INIT;
    }

    public static synchronized void softReset() {
        sessionId = null;
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

    // Simple state machine

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
}
JAVA

echo "[OK] Wrote GhostNetSession.java"

# 3) GhostDebugEventBus.java
cat <<'JAVA' > client_android/app/src/main/java/com/securecall/app/debug/GhostDebugEventBus.java
package com.securecall.app.debug;

import android.util.Log;

/**
 * Minimal debug event bus stub.
 */
public class GhostDebugEventBus {

    private static final String TAG = "GHOST_DEBUG";

    public static void post(String tag, String msg) {
        Log.d(tag != null ? tag : TAG, msg != null ? msg : "");
    }

    public static void postSessionKeysPreview(String tagPrefix, byte[] rx, byte[] tx, byte[] salt) {
        StringBuilder sb = new StringBuilder();
        sb.append("rx=").append(rx != null ? rx.length : 0)
          .append(" tx=").append(tx != null ? tx.length : 0)
          .append(" salt=").append(salt != null ? salt.length : 0);
        post(tagPrefix != null ? tagPrefix : "SESS_KEYS", sb.toString());
    }
}
JAVA

echo "[OK] Wrote GhostDebugEventBus.java"

echo "== patch_035 done =="

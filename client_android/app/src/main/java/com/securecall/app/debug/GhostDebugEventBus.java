package com.securecall.app.debug;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * PATCH 240:
 * Einfacher globaler Event-Bus für Debug-/Status-Events.
 * Kann von Kotlin- und Java-Code benutzt werden.
 */
public final class GhostDebugEventBus {

    private GhostDebugEventBus() {
        // no instance
    }

    public static final class Event {
        private final String tag;
        private final String message;
        private final long timestamp;

        public Event(String tag, String message, long timestamp) {
            this.tag = tag;
            this.message = message;
            this.timestamp = timestamp;
        }

        public String getTag() {
            return tag;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public interface Listener {
        void onEvent(Event event);
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static void post(String tag, String message) {
        long now = System.currentTimeMillis();
        Event event = new Event(tag, message, now);
        for (Listener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable t) {
                // Listener-Fehler sollen den Bus nicht stoppen
                t.printStackTrace();
            }
        }
    }
}

    /**
     * PATCH 242:
     * Hilfsfunktion, um SessionKeys kurz-formatiert ins Log zu werfen.
     * Kann von Debug-Code genutzt werden.
     */
    public static void postSessionKeysPreview(String tagPrefix, byte[] rx, byte[] tx, byte[] salt) {
        String summary = "rx=" + (rx != null ? rx.length : 0)
                + " tx=" + (tx != null ? tx.length : 0)
                + " salt=" + (salt != null ? salt.length : 0);
        post(tagPrefix != null ? tagPrefix : "SESS_KEYS", summary);
    }

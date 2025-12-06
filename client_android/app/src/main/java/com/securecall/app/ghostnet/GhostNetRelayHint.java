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

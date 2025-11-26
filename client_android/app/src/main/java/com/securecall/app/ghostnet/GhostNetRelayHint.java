
    // BACKEND-28: RelayHint Validierung
    public boolean isValid() {
        if (host == null || host.isEmpty()) return false;
        if (port <= 0 || port > 65535) return false;
        return true;
    }

    // BACKEND-28: kompaktes Format für Debugging
    public String asCompactString() {
        return host + ":" + port;
    }

    // BACKEND-28: Override für bessere toString-Ausgabe
    @Override
    public String toString() {
        return "GhostNetRelayHint{host='" + host + "', port=" + port + "}";
    }

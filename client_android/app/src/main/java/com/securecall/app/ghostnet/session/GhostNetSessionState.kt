package com.securecall.app.ghostnet.session

// BACKEND-41 / ANDROID-03:
// Standard-Session-Zustände
enum class GhostNetSessionState {
    IDLE,          // keine Verbindung
    CONNECTING,    // WebSocket handshake läuft
    ACTIVE,        // voll aktiv, Transport darf laufen
    DEAD           // getrennt, Transport sofort stoppen
}

// BACKEND-67 (PATCH 200): Erweiterte States für Handshake/Setup
// Nur anhängen, NICHT ersetzen
enum class ExtendedState {
    HANDSHAKE_OUTGOING,
    HANDSHAKE_INCOMING,
    SESSION_ESTABLISHED
}

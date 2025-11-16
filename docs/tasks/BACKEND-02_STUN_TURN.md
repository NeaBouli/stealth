# BACKEND-02 – Stable Signaling & STUN/TURN Integration

## Ziel
Erweiterung des MVP-Signaling-Servers um stabile Sessions, Heartbeats, Timeouts und STUN/TURN-Unterstützung.

## Erwartetes Ergebnis
Das Backend enthält nach Abschluss:

- Integration eines STUN/TURN-Servers (empfohlen: Coturn)
- Session-Verwaltung mit automatischem Cleanup (Timeouts)
- Heartbeat-Mechanismus für WebSocket-Verbindungen
- Verbesserte Fehlerbehandlung
- Optionale TLS-Unterstützung für produktionsnahe Umgebungen

## Komponenten

### 1. STUN/TURN Integration
- Serverempfehlung: **Coturn**
- Konfiguration:
  - UDP + TCP aktivieren
  - realm definieren
  - credentials definieren
- Backend muss ICE-Kandidaten korrekt weiterleiten.

### 2. Session Handling
- einfacher In-Memory-Store
- Session-Timeout (empfohlen: 60–120 Sekunden)
- Cleanup-Worker alle 30 Sekunden

### 3. WS Heartbeats
- Ping/Pong alle X Sekunden
- automatisch Session schließen bei Timeout

### 4. Fehlerbehandlung
- REST-Endpoints müssen klare Fehlercodes liefern
- WebSocket trennt bei ungültigen Nachrichten

## Nicht-Ziele
- keine HA/Load-Balancing
- keine Persistenz
- keine Token-/User-Authentifizierung

## Developer FAQ

**Frage:** Welchen TURN/STUN-Server sollen wir nutzen?  
**Antwort:** Coturn ist die Empfehlung. Stabil, gut dokumentiert, weit verbreitet.

**Frage:** Ist TLS notwendig?  
**Antwort:** Für MVP nein, für spätere Phasen ja.

**Frage:** Wo werden TURN/STUN-Credentials gespeichert?  
**Antwort:** Lokal in Backend-Konfig, später durch Management-API.


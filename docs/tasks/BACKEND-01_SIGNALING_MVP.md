# BACKEND-01 – Signaling Server MVP

## Ziel
Erstellung eines minimal funktionsfähigen Signaling-Servers als Grundlage für Call-Aufbau, Antworten und Abbruch.

## Erwartetes Ergebnis
Ein Backend-Projekt unter `backend/` mit:

- REST-Endpunkten:
  - POST /register
  - POST /invite
  - POST /answer
  - POST /cancel
- WebSocket-Endpoint für Live-Signaling
- Minimaler Session-Speicher (In-Memory)
- Technisches Logging ohne Metadaten

## Technologie
Erlaubte Implementierungssprachen:  
- Node.js (Express + ws)  
- Go (net/http + gorilla/websocket)  
- Rust (axum + tokio-tungstenite)

## Nicht-Ziele
- Keine Persistenz  
- Keine Authentifizierung  
- Keine Skalierung oder Load-Balancing  
- Keine STUN/TURN-Integration (kommt in BACKEND-02)

## Developer FAQ

**Frage:** Muss der Server produktionsreif sein?  
**Antwort:** Nein, reine MVP-Funktionalität reicht.

**Frage:** Dürfen wir Logs schreiben?  
**Antwort:** Ja, aber ausschließlich technische Fehlerlogs ohne User-Metadaten.

**Frage:** Muss TLS bereits integriert werden?  
**Antwort:** Nein, in Phase 1 optional.


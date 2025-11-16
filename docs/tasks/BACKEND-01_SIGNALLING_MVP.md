# BACKEND-01 – Signaling Server MVP

## Ziel
Bereitstellung eines minimal funktionsfähigen Signaling-Servers, der:
1. Clients registriert,
2. Call-Invites/Answers/Cancels verwaltet,
3. Echtzeit-Events via WebSocket überträgt.

Dieses Modul bildet das Fundament für jegliche Kommunikation.

---

## Erwartetes Ergebnis

Ein lauffähiger Signaling-Server mit:

- REST-Endpunkten:
  - POST /register
  - POST /invite
  - POST /answer
  - POST /cancel
- WebSocket für Live-Events:
  - CALL_INVITE
  - CALL_ACCEPT
  - CALL_END

Zielzustand: Clients A und B können sich registrieren und einen Call einleiten.

---

## Minimalarchitektur

### 1. REST
- JSON-basierte Requests
- keine Authentifizierung in MVP
- keine Persistenz (In-Memory reicht)

### 2. WebSocket
- Jeder Client verbindet sich über eindeutige Session-ID
- Server pusht Events an passende Clients

### 3. Datenstrukturen
- ClientStore (In-Memory)
- CallStore (In-Memory)
- Message-Typen

---

## Sprache / Framework
Erlaubt:
- Node.js (Express + WS)
- Go (Gin + Gorilla/WebSocket)
- Rust (Axum + Tokio/Tungstenite)

Empfohlen: **Go** wegen Stabilität & Performance.

---

## Tests (MVP)
- A registriert → OK
- B registriert → OK
- A lädt B ein → WS liefert CALL_INVITE
- B akzeptiert → WS liefert CALL_ACCEPT
- Cancel → WS liefert CALL_END

---

## Developer FAQ

**Frage:** Müssen Logs geschrieben werden?  
**Antwort:** Ja, aber nur technische Logs. Keine Userdaten, keine Metadaten.

**Frage:** Muss der Code schon skalieren?  
**Antwort:** Nein. Single-Instance reicht.

**Frage:** Persistenz nötig?  
**Antwort:** Nein. Alles In-Memory im MVP.


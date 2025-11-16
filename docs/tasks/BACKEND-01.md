# TASK: BACKEND-01 – Signaling Server MVP

## 1. Ziel des Tasks
Entwicklung eines minimalen, aber lauffähigen Signaling-Servers für den Aufbau verschlüsselter Audio-Sessions.

Der Server bildet das Fundament für:
- Registrierung pseudonymer Identitäten
- Call Invites
- Call Answers
- Call Cancels
- Austausch von ICE-Kandidaten (für WebRTC/QUIC)
- WebSocket-basierte Live-Signalisierung

Dies ist **Phase 1** der Backend-Entwicklung.

---

## 2. Hintergrund / Kontext
Der SecureCall-Client benötigt einen zentralen Punkt, um:
- Gesprächspartner zu finden
- Session-Parameter auszutauschen
- GhostNet-Verbindungen auszuhandeln

Signaling enthält **keine Schlüssel** und **keine Inhalte**.  
Es ist reiner „Telefonist“ und speichert keine Metadaten.

---

## 3. Anforderungen (Requirements)
- [ ] REST-Endpunkte implementieren:
  - `POST /register`
  - `POST /invite`
  - `POST /answer`
  - `POST /cancel`
- [ ] WebSocket-Unterstützung:
  - `/ws` für Call-Events
- [ ] keinerlei persistente Speicherung
- [ ] Server darf keine personenbezogenen Daten speichern
- [ ] UUID-basierte Session-IDs
- [ ] Logging ausschließlich technisch, keine Metadaten
- [ ] Einfache In-Memory-Sitzungsverwaltung

---

## 4. Implementationsdetails

### 4.1 Sprache / Framework
Eine der folgenden Optionen (Entwickler darf wählen):
- **Node.js (Express + ws)**
- **Go (net/http + gorilla/websocket)**
- **Rust (Axum + Tokio + tungstenite)**

### 4.2 Empfohlenes Projekt-Layout (Beispiel Go)
backend/
├── cmd/
│ └── signaling/
│ └── main.go
├── internal/
│ ├── handlers/
│ ├── sessions/
│ ├── websocket/
│ └── utils/
└── go.mod

shell
Code kopieren

### 4.3 REST-API-Verträge
#### POST /register
Input:
{ "public_key": "<base64>" }

makefile
Code kopieren
Output:
{ "user_id": "<uuid>" }

shell
Code kopieren

#### POST /invite
{ "from": "<uuid>", "to": "<uuid>" }

shell
Code kopieren

#### POST /answer
{ "from": "<uuid>", "to": "<uuid>", "accepted": true/false }

shell
Code kopieren

#### POST /cancel
{ "from": "<uuid>", "to": "<uuid>" }

yaml
Code kopieren

### 4.4 WebSocket Flow
Events:
- `invite`
- `answer`
- `cancel`
- `ice-candidate`

Transportformat: JSON Frames.

### 4.5 Timeouts & Cleanup
- Session Cleanup nach 60–120 Sekunden Inaktivität
- WebSocket Timeouts 30–45 Sekunden Ping/Pong

---

## 5. Deliverables
- Verzeichnis `backend/` beinhaltet funktionierenden MVP
- README.md für Backend (Setup + Start)
- REST + WS lauffähig lokal
- Kein Datenbankbedarf

---

## 6. Tests
### 6.1 Unit Tests
- Session-Handling
- WebSocket-Broadcast
- REST-Endpunkte

### 6.2 Integration Tests
- Invite → Answer → Verbindung steht
- Cancel → Verbindung abgebrochen

### 6.3 Security Tests
- kein Logging sensibler Daten
- Logging anonymisiert
- In-Memory Session Isolation

### 6.4 Acceptance Criteria
- [ ] Server startet ohne Fehler
- [ ] REST-Endpunkte erreichbar
- [ ] WebSocket-Verbindungen funktionieren
- [ ] Sessions funktionieren
- [ ] Keine Persistenz

---

## 7. Q&A (vorausgedachte Fragen)
**F:** Muss bereits TURN/STUN integriert werden?  
**A:** Nein, das folgt in BACKEND-02.

**F:** Dürfen wir Docker verwenden?  
**A:** Ja, optional. Ein späterer Task wird Dockerfiles standardisieren.

**F:** Muss HTTPS erzwungen werden?  
**A:** Für MVP nicht zwingend — später jedoch Pflicht.

---

## 8. Referenzen
- docs/ARCHITECTURE_OVERVIEW.md
- PROJECT_MASTER_PLAN.json
- SECURITY_DESIGN.md


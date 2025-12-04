SecureCall Signaling Server

BACKEND-19 – Session Cleanup (automatische Loeschung alter Sitzungen)

Dieses Update fuehrt ein serverseitiges Bereinigungsmodul ein, das beendete
Calls automatisch aus der Session Registry entfernt. Damit bleibt der
Speicherverbrauch stabil, und es sammeln sich keine alten, nicht mehr
relevanten Sitzungen an. Das Cleanup laeuft regelmaessig im Hintergrund und
loescht abgeschlossene oder fehlgeschlagene Anrufe nach 60 Sekunden.

-------------------------------------------------------------------------------
1. Zweck der Session Cleanup Funktion
-------------------------------------------------------------------------------

Ohne Bereinigung koennten sich folgende Probleme ergeben:

- "leere" oder abgebrochene Sessions bleiben ewig gespeichert
- steigender Speicherverbrauch
- ungenaue Statistik
- fehlerhafte Call-Historie fuer spaetere Module
- inkonsistente Logs

Durch BACKEND-19 wird der Speicher sauber gehalten.

-------------------------------------------------------------------------------
2. Funktionsweise (session_registry.js)
-------------------------------------------------------------------------------

Jede Session besitzt:
- sessionId
- caller
- callee
- state
- createdAt
- updatedAt
- endedAt (nur fuer ENDED/FAILED)

Die neue Funktion cleanupSessions():

- iteriert durch alle Sessions
- sucht ENDED oder FAILED
- prueft, ob endedAt aelter als 60000 ms ist
- entfernt die Session aus der Registry
- gibt eine Liste geloeschter sessionIds zurueck

-------------------------------------------------------------------------------
3. Integration in server.js
-------------------------------------------------------------------------------

Ein neuer Timer fuehrt alle 30 Sekunden ein Session-Cleanup aus:

setInterval(() => {
  const removed = registry.cleanupSessions();
  if (removed.length) {
    broadcast({ type: "SESSION_CLEANUP", removed });
  }
}, 30000);

Broadcast Beispiel:

{
  "type": "SESSION_CLEANUP",
  "removed": ["session-abc", "session-def"]
}

Dies informiert alle verbundenen Clients ueber geloeschte Sessions.

-------------------------------------------------------------------------------
4. Vorteile fuer das Gesamtsystem
-------------------------------------------------------------------------------

- Stabiler RAM-Verbrauch
- Keine veralteten Sessions mehr
- Genauere Call-Datenbasis
- Voraussetzung fuer spaetere Call-Historie-APIs
- Bessere Debug- und Analyse-Moeglichkeiten
- Kompatibel mit allen CALL_* Events aus BACKEND-18

-------------------------------------------------------------------------------
5. Status BACKEND-19
-------------------------------------------------------------------------------

- session_registry.js erweitert
- server.js Cleanup-Loop integriert
- Broadcast-Nachricht SESSION_CLEANUP implementiert
- Logging konsistent gehalten
- keine Breaking Changes

-------------------------------------------------------------------------------
6. Naechster Schritt
-------------------------------------------------------------------------------

BACKEND-20: Call Routing Layer (Mapping von Session → GhostNet Pfad)


---

# BACKEND-20 — Routing Layer (Sessions + Call States)

Dieses Modul erweitert das Signaling-System um:
- Session-Tracking
- Routing-Tabelle
- Call Lifecycle (INVITE → RINGING → ACCEPTED → ACTIVE → ENDED)
- Validierung eingehender Nachrichten
- einfache Websocket-basierte Steuerung

## 1. Routing-Tabelle (In-Memory)

Die Routing-Tabelle befindet sich im Speicher des Signaling-Servers.

Struktur (Beispiel):

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "from": "peer-A",
  "to": "peer-B",
  "state": "ACTIVE",
  "created": 1731931274000
}
Möglich Zustände:
State	Bedeutung
INVITE	A möchte B anrufen
RINGING	B wurde benachrichtigt
ACCEPTED	B hat angenommen, GhostNet wird aufgebaut
ACTIVE	Call läuft
ENDED	Gespräch beendet, Session zur Löschung markiert

2. Call Lifecycle (State Machine)
objectivec
Code kopieren
CALL_INVITE
    ↓
RINGING
    ↓ (CALL_ACCEPT)
ACCEPTED
    ↓ (GhostNet ready)
ACTIVE
    ↓ (CALL_END)
ENDED  → Session wird gelöscht
3. WebSocket-Kommandos (MVP)
CALL_INVITE
json
Code kopieren
{
  "type": "CALL_INVITE",
  "to": "peer-123"
}
CALL_ACCEPT
json
Code kopieren
{
  "type": "CALL_ACCEPT",
  "sessionId": "..."
}
CALL_END
json
Code kopieren
{
  "type": "CALL_END",
  "sessionId": "..."
}
Der Server validiert alle Eingaben und passt Routing + State Machine an.

4. REST-Endpunkte (Debug)
Liste aller aktiven Routen
bash
Code kopieren
GET /routing/list
Beispielausgabe:

json
Code kopieren
{
  "routes": [
    {
      "sessionId": "...",
      "from": "peerA",
      "to": "peerB",
      "state": "ACTIVE"
    }
  ]
}
5. Tools (für Tests)
flooding-test.sh
Testet Last auf /signal.

burst.sh
Schickt schnelle Push-Nachrichten.

routing-test.sh (neu, BACKEND-20)
Testet:

CALL_INVITE

CALL_ACCEPT

Routing-Liste

CALL_END

6. Nächste Schritte (BACKEND-21)
Heartbeats & WS-Ping

Idle-Timeout für Sessions

Auto-Cleanup abgelaufener Routen

GhostNet-Pre-Handshake vorbereiten


---

# BACKEND-21 — Heartbeat, Ping-Pong & Session-Timeout

Dieses Modul erweitert das Signaling-System um:

- WebSocket Heartbeat (Ping/Pong)
- automatische Erkennung toter Clients
- automatisches Aufräumen alter Sessions
- Aktualisierung von Timestamp-Feldern für Clients & Sessions

## 1. Heartbeat (Ping/Pong)

Der Server sendet alle **5 Sekunden** einen Ping an alle WebSocket-Clients.

Jeder Client muss darauf mit **Pong** antworten.

Wenn ein Client **> 30 Sekunden** nicht mehr reagiert:
- Verbindung wird geschlossen
- Client wird aus der Registry gelöscht

## 2. Client-Tracking

In `server.js` wird eine Client-Map geführt:

clients = {
"<client-id>": {
ws: <WebSocket>,
lastSeen: <timestamp>
}
}

markdown
Code kopieren

`lastSeen` wird aktualisiert:
- bei jeder Nachricht
- bei jedem Pong

## 3. Session-Timeout

Die Routing-Tabelle erhält ein Feld `updated`.

Wenn keine Aktion mehr erfolgt, wird eine Session nach **30 Sekunden** gelöscht.

Session-Struktur:

{
sessionId,
from,
to,
state,
created,
updated
}

makefile
Code kopieren

Die Cleanup-Logik erfolgt in `heartbeat.js`.

## 4. Debug-API

Endpunkt:

GET /routing/list

markdown
Code kopieren

Listet alle aktiven Sessions auf.

Sessions verschwinden automatisch, wenn sie ablaufen oder per `CALL_END` beendet werden.

## 5. Testskript

Das Skript `tools/test_routing.sh` kann weiterhin verwendet werden.
Es testet jetzt zusätzlich:

- Session-Ablauf nach Timeout
- WS-Heartbeat (Pings im Log sichtbar)

## 6. Nächste Schritte (BACKEND-22)

- Heartbeat-Protokoll im Client implementieren (Android)
- KeepAlive für GhostNet bereitstellen
- Multi-Device-Session-Model vorbereiten
- Load-Balancing für Signaling Nodes


# BACKEND-23 — GhostNet Pre-Handshake (Spezifikation, MVP)

Dieses Modul beschreibt den Pre-Handshake zwischen
Signaling-Server und den GhostNet-Clients (Android, später ggf. weitere).

Ziel:
- Vor dem eigentlichen Audio-Stream wird ein separater "GhostNet"-Kanal
  aufgebaut (z.B. QUIC/WebRTC).
- Der Pre-Handshake tauscht alle notwendigen Parameter aus, ohne
  Metadaten unnötig zu leaken.

## Begriffe

- `sessionId`  — vom Signaling-Server vergebene Call-Session
- `ghostNetId` — eindeutige ID für den GhostNet-Transport-Kanal
- `keyMaterial` — vom Client generiertes Ephemeral Key-Material (z.B. X25519 Public Key)

## Nachrichten (MVP)

### 1. CLIENT → SERVER: GHOST_PREPARE

- Transport: WebSocket `/signal`
- Richtung: Client → Signaling-Server
- Zeitpunkt: Nachdem eine `CALL_SESSION` aktiv ist

Payload (JSON):

```json
{
  "type": "GHOST_PREPARE",
  "sessionId": "<SESSION-ID>",
  "clientId": "<CLIENT-ID>",
  "keyMaterial": "<BASE64-EPHEMERAL-PUBKEY>"
}
2. SERVER → CLIENT: GHOST_ACK
Antwort auf GHOST_PREPARE.

json
Code kopieren
{
  "type": "GHOST_ACK",
  "sessionId": "<SESSION-ID>",
  "ghostNetId": "<GHOST-NET-ID>",
  "relayHints": [
    { "host": "relay1.example", "port": 443 },
    { "host": "relay2.example", "port": 8443 }
  ]
}
MVP-Ziel (BACKEND-23)
Server akzeptiert GHOST_PREPARE und loggt die Daten.

Server generiert eine einfache ghostNetId (z.B. UUID).

Server antwortet mit GHOST_ACK (statisch konfigurierte Relays).

Noch kein echter QUIC/WebRTC-Transport, nur Signaling-Schicht.

Nächste Schritte (BACKEND-24+)
Aufbau eines separaten GhostNet-Transports (QUIC/WebRTC).

Ableitung von Session-Keys aus keyMaterial (Core Crypto).

Multi-Hop Routing (Premium/OS).

## Routing-Modul (ghostnet_router.js)

Das Modul `ghostnet_router.js` kapselt die Auswahl der `relayHints`.
Vorläufig ist die Liste statisch, später kann hier:

- Region/Geo-basiertes Routing
- Ausfallsicherheit / Health-Checks
- Multi-Hop-Pfade (Premium / OS)
- Policy-basiertes Routing

implementiert werden.

## GhostNet Transport Server (Stub)

Die Datei `ghostnet_server_stub.js` stellt einen einfachen HTTP-Server bereit,
der als Platzhalter für den zukünftigen GhostNet-Transport dient.

- `/health` → Health-Check Endpoint
- Port: 9090 (Standard, konfigurierbar)

Spätere Ausbaustufen (BACKEND-25+):

- QUIC- oder WebRTC-basierter Audio-/Datenkanal
- Nutzung der `ghostNetId` + `relayHints` aus dem Pre-Handshake
- Multi-Hop-Routing und Latenz-Optimierung

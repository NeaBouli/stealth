# SecureCall Signaling Server (BACKEND-01)

Minimaler Signaling-Server für SecureCall.
Dies ist die MVP-Version (Echo-Server), um spätere
WebRTC/QUIC-Signalisierung testen zu können.

## Features (MVP)
- HTTP-Status-Endpoint `/`
- WebSocket-Endpoint `/signal`
- Client-Verbindungen werden akzeptiert
- Nachrichten werden 1:1 zurückgesendet (Echo)

## Starten (lokal)

1. Dependencies installieren:

    npm install

2. Server starten:

    npm run dev

Server läuft anschließend auf Port **8080**.

## Nächste Schritte (BACKEND-02)
- Session-IDs
- Peer-Mapping
- Call-Invite / Call-Accept Nachrichten
- Timeout-Management
- Heartbeats/Pings


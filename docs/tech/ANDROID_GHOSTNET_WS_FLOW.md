# Android GhostNet WebSocket Flow (Skeleton)

Status: DEV / Debug Only

## Ziel

Dieser Flow beschreibt die minimale WebSocket-Basis für GhostNet:

- Android-Client:
  - nutzt `GhostNetWebSocketClient` (OkHttp-basiert),
  - kann Binärframes (`byte[]`) senden.
- Backend:
  - einfacher Node.js-WebSocket-Server (`ghostnet_echo_server.js`),
  - spiegelt eingehende Frames unverändert zurück.

Noch NICHT implementiert:

- FrameV1-Parsing / -Aufbau,
- Crypto (Handshake, Keying, Opus),
- Integration in GhostNetworkSender/GhostNetworkReceiver.

## Komponenten

### 1. Android – GhostNetWebSocketClient

Package:

- `com.securecall.app.ghostnet.transport.ws.GhostNetWebSocketClient`

Funktionen:

- `getInstance()` – Singleton-Access
- `connect(String url)` – baut Verbindung auf (z.B. `ws://10.0.2.2:8080`)
- `isConnected()` – einfacher Status
- `sendFrame(byte[] data)` – sendet Binärpayload
- `close()` – schließt Verbindung

Logging-Tag: `GHOST_WS`

### 2. Backend – ghostnet_echo_server.js

Pfad:

- `backend/ghostnet_echo_server.js`

Funktionen:

- startet einen WebSocket-Server (Port `8080` oder `GHOSTNET_ECHO_PORT`),
- loggt eingehende Nachrichten,
- sendet sie 1:1 zurück.

Start (im `backend`-Ordner):

```bash
npm install ws   # einmalig
node ghostnet_echo_server.js
Nächste Schritte (für zukünftige Patches)
GhostNetworkSender
sendRawNetworkFrame(byte[]) → GhostNetWebSocketClient.sendFrame(...)

GhostNetworkReceiver
WebSocket onMessage(ByteString) → Inject in bestehende Inbound-Queue

E2E-Test-Idee
Debug-Frame bauen (z.B. einfacher Header + PCM),

über WebSocket zum Echo-Server senden,

Antwort zurück in MediaRouterInboundStub leiten,

Beep aus dem Netzwerk hören.

Bis dahin dient dieser Patch nur als Transport-Skelett, um später sauber anschließen zu können.

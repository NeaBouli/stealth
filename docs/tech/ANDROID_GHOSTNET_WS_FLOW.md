# Android GhostNet WebSocket Flow (Draft)

This document describes how the Android client talks to the local
GhostNet echo server via WebSocket, using the FrameV1 wire format.

It is intentionally simple and log-heavy so that future developers
can extend it into a full GhostNet transport layer.

---

## 1. Components

- **GhostNetWebSocketClient**
  - Package: `com.securecall.app.ghostnet.transport.ws`
  - Uses OkHttp WebSocket
  - Provides:
    - `connect()` / `connect(String url)`
    - `disconnect()`
    - `sendText(String text)`
    - `sendBinary(byte[] data)`
    - `sendControlHello()` – sends a FrameV1 control frame

- **GhostNet Echo Server**
  - File: `backend/ghostnet_echo_server.js`
  - Uses `ws` npm package
  - For each binary message:
    - parses a FrameV1 header (if at least 12 bytes),
    - logs VERSION / TYPE / FLAGS / KEY_ID / SESSION_ID / LENGTH,
    - echoes the full binary frame back to the client.

---

## 2. Default topology

For local development:

- Echo server:
  - runs on your dev machine
  - default: `ws://0.0.0.0:8080`
  - can be changed via `GHOSTNET_ECHO_PORT` env var

- Android client:
  - uses `GhostNetWebSocketClient.DEFAULT_URL`:

    - Emulator: `ws://10.0.2.2:8080` (maps to host loopback)
    - Real device: use your host LAN IP, e.g. `ws://192.168.0.10:8080`

Future work may move this to a configuration screen or build-time
flavor, but for now a hardcoded URL is sufficient.

---

## 3. CONTROL_HELLO frame (TYPE=CONTROL)

The first practical use of FrameV1 is a simple "CONTROL_HELLO" frame
that can be sent after connect.

Header layout (12 bytes, unencrypted):

- VERSION    = 0x01
- TYPE       = 0x02 (CONTROL)
- FLAGS      = 0x00
- KEY_ID     = 0x00
- SESSION_ID = 0x00000001 (placeholder)
- LENGTH     = 0x00000000 (no payload)

In the current implementation:

- `GhostNetWebSocketClient.sendControlHello()` builds this header.
- No payload is attached (payload length = 0).
- The echo server logs the header fields and sends the frame back.

This is enough to verify:

1. WebSocket connectivity works.
2. FrameV1 header is well-formed.
3. Server and client see the same TYPE/VERSION/SESSION_ID/LENGTH.

---

## 4. Example usage from UI code (future work)

The UI (e.g. Start Call button) can later trigger:

```java
GhostNetWebSocketClient client = GhostNetWebSocketClient.getInstance();
client.connect();            // uses default URL
client.sendControlHello();   // sends CONTROL_HELLO (FrameV1)
This wiring is not part of this patch and should be done as part
of the Track A1 / Start Call integration, so that UI and transport
concerns stay separate.

5. Next steps
Track A1:

Wire the Start Call button to connect + sendControlHello().

Log lifecycle and failures clearly with the GHOSTNET_WS tag.

Track A2:

Add KEEPALIVE (PING/PONG) FrameV1 handling (TYPE=KEEPALIVE).

Track B (later):

Introduce AUDIO_OPUS_FRAME (TYPE=AUDIO) and route decoded PCM
into MediaRouterInboundStub.handleDecodedPcm(...).


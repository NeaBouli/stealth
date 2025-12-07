#!/bin/bash
set -e

echo "== patch_015: GhostNet WS CONTROL_HELLO skeleton =="

#
# 1) GhostNetWebSocketClient.java – WS-Client mit CONTROL_HELLO-FrameV1
#
cat <<'JAVA' > client_android/app/src/main/java/com/securecall/app/ghostnet/transport/ws/GhostNetWebSocketClient.java
package com.securecall.app.ghostnet.transport.ws;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * GhostNetWebSocketClient
 *
 * Minimal, log-heavy WebSocket client used for:
 * - connecting to the GhostNet echo server
 * - sending FrameV1 CONTROL_HELLO frames (TYPE=CONTROL)
 *
 * IMPORTANT:
 *  - This class does NOT start itself.
 *  - UI code (e.g. Start Call button) should call:
 *
 *      GhostNetWebSocketClient client = GhostNetWebSocketClient.getInstance();
 *      client.connect();            // uses DEFAULT_URL
 *      client.sendControlHello();   // sends FrameV1 CONTROL_HELLO with LENGTH=0
 *
 *  - For emulator, DEFAULT_URL works (10.0.2.2).
 *  - For real devices, you may have to point to your LAN IP instead.
 */
public class GhostNetWebSocketClient extends WebSocketListener {

    private static final String TAG = "GHOSTNET_WS";

    /**
     * Default URL:
     *  - Emulator:    ws://10.0.2.2:8080
     *  - Real device: replace with your Mac/Server LAN IP, e.g. ws://192.168.0.10:8080
     */
    private static final String DEFAULT_URL = "ws://10.0.2.2:8080";

    private static GhostNetWebSocketClient instance;

    private final OkHttpClient client;
    private WebSocket webSocket;
    private String currentUrl;
    private boolean isConnecting;

    private GhostNetWebSocketClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized GhostNetWebSocketClient getInstance() {
        if (instance == null) {
            instance = new GhostNetWebSocketClient();
        }
        return instance;
    }

    public synchronized void connect() {
        connect(DEFAULT_URL);
    }

    public synchronized void connect(String url) {
        if (webSocket != null) {
            Log.d(TAG, "connect(): already have WebSocket, url=" + currentUrl);
            return;
        }
        isConnecting = true;
        currentUrl = url;
        Log.d(TAG, "connect(): opening WebSocket to " + url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        // This will trigger onOpen/onFailure callbacks in this listener.
        webSocket = client.newWebSocket(request, this);
    }

    public synchronized void disconnect() {
        if (webSocket != null) {
            Log.d(TAG, "disconnect(): closing WebSocket");
            webSocket.close(1000, "client disconnect");
            webSocket = null;
        }
        isConnecting = false;
    }

    public synchronized boolean isConnected() {
        return webSocket != null && !isConnecting;
    }

    public synchronized void sendBinary(byte[] data) {
        if (webSocket == null) {
            Log.w(TAG, "sendBinary(): no active WebSocket, dropping frame (" + data.length + " bytes)");
            return;
        }
        webSocket.send(ByteString.of(data, 0, data.length));
        Log.d(TAG, "sendBinary(): sent " + data.length + " bytes");
    }

    public synchronized void sendText(String text) {
        if (webSocket == null) {
            Log.w(TAG, "sendText(): no active WebSocket, dropping text: " + text);
            return;
        }
        webSocket.send(text);
        Log.d(TAG, "sendText(): \"" + text + "\"");
    }

    /**
     * Build and send a FrameV1 CONTROL_HELLO frame.
     *
     * Layout (12-byte header, no payload):
     *  VERSION    = 0x01
     *  TYPE       = 0x02 (CONTROL)
     *  FLAGS      = 0x00
     *  KEY_ID     = 0x00
     *  SESSION_ID = 0x00000001 (placeholder)
     *  LENGTH     = 0x00000000 (no payload)
     */
    public synchronized void sendControlHello() {
        byte[] frame = buildControlHelloFrame();
        Log.d(TAG, "sendControlHello(): sending CONTROL_HELLO, frameLen=" + frame.length);
        sendBinary(frame);
    }

    private byte[] buildControlHelloFrame() {
        byte version = 0x01;
        byte type = 0x02;  // CONTROL
        byte flags = 0x00;
        byte keyId = 0x00;
        int sessionId = 1; // placeholder
        int length = 0;    // no payload

        byte[] frame = new byte[12];

        frame[0] = version;
        frame[1] = type;
        frame[2] = flags;
        frame[3] = keyId;

        // SESSION_ID (little-endian)
        frame[4] = (byte) (sessionId & 0xFF);
        frame[5] = (byte) ((sessionId >> 8) & 0xFF);
        frame[6] = (byte) ((sessionId >> 16) & 0xFF);
        frame[7] = (byte) ((sessionId >> 24) & 0xFF);

        // LENGTH (little-endian)
        frame[8]  = (byte) (length & 0xFF);
        frame[9]  = (byte) ((length >> 8) & 0xFF);
        frame[10] = (byte) ((length >> 16) & 0xFF);
        frame[11] = (byte) ((length >> 24) & 0xFF);

        return frame;
    }

    // --- WebSocketListener callbacks ---

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "onOpen(): WebSocket connected, url=" + currentUrl);
        synchronized (this) {
            isConnecting = false;
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "onMessage(text): \"" + text + "\"");
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        byte[] data = bytes.toByteArray();
        Log.d(TAG, "onMessage(binary): received " + data.length + " bytes from server");
        // NOTE:
        // For now, we only log the incoming data length.
        // Parsing / routing of FrameV1 is a separate milestone.
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "onClosing(): code=" + code + ", reason=" + reason);
        webSocket.close(code, reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "onClosed(): code=" + code + ", reason=" + reason);
        synchronized (this) {
            if (this.webSocket == webSocket) {
                this.webSocket = null;
            }
            isConnecting = false;
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "onFailure(): " + t.getMessage(), t);
        synchronized (this) {
            if (this.webSocket == webSocket) {
                this.webSocket = null;
            }
            isConnecting = false;
        }
    }
}
JAVA

echo "[OK] Wrote GhostNetWebSocketClient.java"

#
# 2) backend/ghostnet_echo_server.js – FrameV1-Header-P parsing & Logging
#
cat <<'JS' > backend/ghostnet_echo_server.js
/**
 * GhostNet Echo Server
 *
 * - Listens on ws://0.0.0.0:8080 (or GHOSTNET_ECHO_PORT)
 * - Echos back any message (text or binary)
 * - If binary >= 12 bytes, parses FrameV1 header and logs it.
 */

const WebSocket = require('ws');

const PORT = process.env.GHOSTNET_ECHO_PORT
  ? parseInt(process.env.GHOSTNET_ECHO_PORT, 10)
  : 8080;

function parseFrameV1Header(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) {
    return null;
  }

  const version   = buf.readUInt8(0);
  const type      = buf.readUInt8(1);
  const flags     = buf.readUInt8(2);
  const keyId     = buf.readUInt8(3);
  const sessionId = buf.readUInt32LE(4);
  const length    = buf.readUInt32LE(8);

  return { version, type, flags, keyId, sessionId, length };
}

const server = new WebSocket.Server({ port: PORT });

console.log(`[GHOSTNET-ECHO] listening on port ${PORT}`);

server.on('connection', (ws, req) => {
  const remote = req.socket && req.socket.remoteAddress
    ? req.socket.remoteAddress
    : 'unknown';

  console.log(`[GHOSTNET-ECHO] client connected from ${remote}`);

  ws.on('message', (data, isBinary) => {
    if (isBinary || Buffer.isBuffer(data)) {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      console.log(`[GHOSTNET-ECHO] received binary frame (${buf.length} bytes)`);

      const header = parseFrameV1Header(buf);
      if (header) {
        console.log(
          `[GHOSTNET-ECHO] FrameV1 header: ` +
          `version=${header.version}, ` +
          `type=${header.type}, ` +
          `flags=${header.flags}, ` +
          `keyId=${header.keyId}, ` +
          `sessionId=${header.sessionId}, ` +
          `length=${header.length}`
        );
      } else {
        console.log('[GHOSTNET-ECHO] binary frame too short for FrameV1 header');
      }

      // Echo back
      ws.send(buf, { binary: true });
    } else {
      console.log(`[GHOSTNET-ECHO] received text: ${data.toString()}`);
      ws.send(data.toString());
    }
  });

  ws.on('close', (code, reason) => {
    console.log(`[GHOSTNET-ECHO] client disconnected: code=${code}, reason=${reason}`);
  });

  ws.on('error', (err) => {
    console.error('[GHOSTNET-ECHO] ws error:', err);
  });
});

server.on('error', (err) => {
  console.error('[GHOSTNET-ECHO] server error:', err);
});
JS

echo "[OK] Wrote backend/ghostnet_echo_server.js"

#
# 3) docs/tech/ANDROID_GHOSTNET_WS_FLOW.md – CONTROL_HELLO im Flow dokumentieren
#
cat <<'MD' > docs/tech/ANDROID_GHOSTNET_WS_FLOW.md
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

MD

echo "[OK] Updated docs/tech/ANDROID_GHOSTNET_WS_FLOW.md"

echo "== patch_015 done =="

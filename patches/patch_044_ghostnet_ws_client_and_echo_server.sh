#!/bin/bash
set -e

echo "== patch_044: add GhostNet WebSocket client + echo server skeleton =="

# 1) Gradle: OkHttp-Dependency in app/build.gradle einfügen
python3 - <<'PY'
from pathlib import Path

path = Path("client_android/app/build.gradle")
txt = path.read_text()

dep_line = "    implementation 'com.squareup.okhttp3:okhttp:4.12.0'\\n"

if "com.squareup.okhttp3:okhttp" in txt:
    print("[INFO] OkHttp dependency already present in app/build.gradle")
else:
    marker = "dependencies {"
    if marker not in txt:
        raise SystemExit("dependencies { block not found in app/build.gradle")
    # Einfach beim ersten Vorkommen einfügen
    txt = txt.replace(marker, marker + "\\n" + dep_line, 1)
    path.write_text(txt)
    print("[OK] Inserted OkHttp dependency into app/build.gradle")
PY

# 2) Java-WebSocket-Client anlegen
mkdir -p client_android/app/src/main/java/com/securecall/app/ghostnet/transport/ws

cat <<'JAVA' > client_android/app/src/main/java/com/securecall/app/ghostnet/transport/ws/GhostNetWebSocketClient.java
package com.securecall.app.ghostnet.transport.ws;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * GhostNetWebSocketClient
 *
 * Debug-/Skeleton-Implementierung eines WebSocket-Clients für GhostNet:
 * - managed eine einzelne Verbindung (Singleton),
 * - erlaubt connect(url), sendFrame(byte[]), close(),
 * - loggt eingehende Frames, leitet sie aber noch nicht in die
 *   eigentliche GhostNet-Pipeline weiter.
 *
 * WICHTIG:
 *  - Dies ist eine reine Dev-/Debug-Basis und kein finales Protokoll.
 *  - Später werden hier FrameV1 / Crypto / Routing integriert.
 */
public class GhostNetWebSocketClient {

    private static final String TAG = "GHOST_WS";

    private static GhostNetWebSocketClient INSTANCE;

    public static synchronized GhostNetWebSocketClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GhostNetWebSocketClient();
        }
        return INSTANCE;
    }

    private final OkHttpClient client;
    private WebSocket socket;
    private boolean connected;

    private GhostNetWebSocketClient() {
        client = new OkHttpClient.Builder().build();
    }

    /**
     * Baut (falls nötig) eine neue WebSocket-Verbindung auf.
     * Mehrfache Aufrufe sind erlaubt, wenn bereits verbunden, passiert nichts.
     */
    public synchronized void connect(String url) {
        if (socket != null && connected) {
            Log.d(TAG, "connect(): already connected");
            return;
        }
        Log.d(TAG, "connect(): opening WebSocket to " + url);

        Request req = new Request.Builder()
                .url(url)
                .build();

        socket = client.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                Log.d(TAG, "onOpen(): WebSocket connected");
                synchronized (GhostNetWebSocketClient.this) {
                    connected = true;
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "onMessage(): received " + bytes.size() + " bytes");
                // TODO (GHOSTNET-TRANSPORT): hier später an GhostNetworkReceiver weitergeben
                // z.B. GhostNetworkReceiver.injectFromWebSocket(bytes.toByteArray());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "onClosing(): code=" + code + " reason=" + reason);
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "onClosed(): code=" + code + " reason=" + reason);
                synchronized (GhostNetWebSocketClient.this) {
                    connected = false;
                    socket = null;
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                Log.e(TAG, "onFailure(): WebSocket error", t);
                synchronized (GhostNetWebSocketClient.this) {
                    connected = false;
                    socket = null;
                }
            }
        });
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    /**
     * Sendet ein Raw-Frame als Binary über den WebSocket.
     * Erwartet spätere Nutzung mit FrameV1 (verschlüsselt).
     */
    public synchronized void sendFrame(byte[] data) {
        if (socket == null) {
            Log.w(TAG, "sendFrame(): no active WebSocket (socket == null)");
            return;
        }
        if (data == null || data.length == 0) {
            Log.w(TAG, "sendFrame(): empty payload");
            return;
        }
        boolean ok = socket.send(ByteString.of(data));
        if (!ok) {
            Log.w(TAG, "sendFrame(): socket.send() returned false");
        } else {
            Log.d(TAG, "sendFrame(): sent " + data.length + " bytes");
        }
    }

    /**
     * Schließt die Verbindung sauber.
     */
    public synchronized void close() {
        if (socket != null) {
            Log.d(TAG, "close(): closing WebSocket");
            socket.close(1000, "client close");
            socket = null;
            connected = false;
        }
    }
}
JAVA

echo "[OK] Wrote GhostNetWebSocketClient.java"

# 3) Backend: einfacher GhostNet Echo-Server (Node.js + ws)
cat <<'JS' > backend/ghostnet_echo_server.js
// ghostnet_echo_server.js
//
// Minimaler WebSocket-Echo-Server für GhostNet-Tests.
// - Nimmt Binärdaten entgegen
// - Spiegelt sie 1:1 zurück
//
// Start (im backend-Verzeichnis):
//   npm install ws   # einmalig
//   node ghostnet_echo_server.js
//
// Standard-Port: 8080 (konfigurierbar via GHOSTNET_ECHO_PORT)

const WebSocket = require('ws');

const PORT = process.env.GHOSTNET_ECHO_PORT || 8080;

const wss = new WebSocket.Server({ port: PORT });

console.log("[GHOSTNET-ECHO] listening on port " + PORT);

wss.on('connection', (ws, req) => {
  const addr = req.socket.remoteAddress + ":" + req.socket.remotePort;
  console.log("[GHOSTNET-ECHO] client connected:", addr);

  ws.on('message', (data, isBinary) => {
    const len = data ? data.length : 0;
    console.log("[GHOSTNET-ECHO] received " + len + " bytes, echoing back");
    ws.send(data, { binary: isBinary === true });
  });

  ws.on('close', () => {
    console.log("[GHOSTNET-ECHO] client disconnected:", addr);
  });

  ws.on('error', (err) => {
    console.error("[GHOSTNET-ECHO] socket error:", err);
  });
});

wss.on('error', (err) => {
  console.error("[GHOSTNET-ECHO] server error:", err);
});
JS

echo "[OK] Wrote backend/ghostnet_echo_server.js"

# 4) Doku: ANDROID_GHOSTNET_WS_FLOW.md
mkdir -p docs/tech

cat <<'MD' > docs/tech/ANDROID_GHOSTNET_WS_FLOW.md
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
MD

echo "[OK] Wrote docs/tech/ANDROID_GHOSTNET_WS_FLOW.md"

echo "== patch_044 done =="

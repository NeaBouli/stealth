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

package com.securecall.app.ghostnet.transport.ws;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class GhostNetWebSocketClient {

    private static final String TAG = "GHOSTNET_WS";

    private static GhostNetWebSocketClient INSTANCE;

    private final OkHttpClient client;
    private WebSocket webSocket;
    private boolean isConnectingOrOpen = false;

    private GhostNetWebSocketClient() {
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        Log.d(TAG, "GhostNetWebSocketClient(): instance created");
    }

    public static synchronized GhostNetWebSocketClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GhostNetWebSocketClient();
            Log.d(TAG, "getInstance(): created new instance");
        } else {
            Log.d(TAG, "getInstance(): reusing existing instance");
        }
        return INSTANCE;
    }

    public synchronized void connect(String url) {
        Log.d(TAG, "connect() called with url=" + url);

        if (url == null || url.isEmpty()) {
            Log.d(TAG, "connect(): url is null/empty, aborting");
            return;
        }

        if (isConnectingOrOpen && webSocket != null) {
            Log.d(TAG, "connect(): already connecting/open, reusing existing socket");
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        Log.d(TAG, "connect(): creating new WebSocket via OkHttp");

        isConnectingOrOpen = true;
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "onOpen(): " + response);
                sendKeepalive();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "onMessage(text): " + text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                Log.d(TAG, "onMessage(bytes, len=" + bytes.size() + "): " + bytes.hex());
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "onClosing(): code=" + code + " reason=" + reason);
                isConnectingOrOpen = false;
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "onClosed(): code=" + code + " reason=" + reason);
                isConnectingOrOpen = false;
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.d(TAG, "onFailure(): " + t + " response=" + response, t);
                isConnectingOrOpen = false;
            }
        });

        Log.d(TAG, "connect(): async WebSocket creation requested");
    }

    public synchronized void sendControlHello() {
        Log.d(TAG, "sendControlHello() called");

        if (webSocket == null) {
            Log.d(TAG, "sendControlHello(): webSocket is null, nothing to send");
            return;
        }

        String payload = "{\"type\":\"CONTROL_HELLO\",\"ts\":" + System.currentTimeMillis() + "}";
        boolean ok = webSocket.send(payload);
        Log.d(TAG, "sendControlHello(): sent text payload, ok=" + ok + " payload=" + payload);
    }

    public synchronized void sendKeepalive() {
        Log.d(TAG, "sendKeepalive() called");

        if (webSocket == null) {
            Log.d(TAG, "sendKeepalive(): webSocket is null, nothing to send");
            return;
        }

        String payload = "{\"type\":\"KEEPALIVE\",\"ts\":" + System.currentTimeMillis() + "}";
        boolean ok = webSocket.send(payload);
        Log.d(TAG, "sendKeepalive(): sent KEEPALIVE text, ok=" + ok + " payload=" + payload);
    }
}

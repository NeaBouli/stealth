package com.securecall.app.ghostnet.transport.ws;


import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import com.securecall.app.ghostnet.call.CallSessionManager;
import com.securecall.app.ghostnet.media.AudioPlaybackStub;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class GhostNetWebSocketClient {

    private static final String TAG = "GHOSTNET_WS";
    private static final long KEEPALIVE_INTERVAL_MS = 5000L;

    private final Handler keepaliveHandler = new Handler(Looper.getMainLooper());
    private final Runnable keepaliveRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                sendKeepalive();
            } finally {
                if (connectionState == ConnectionState.CONNECTED) {
                    keepaliveHandler.postDelayed(this, KEEPALIVE_INTERVAL_MS);
                }
            }
        }
    };


    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private void setConnectionState(ConnectionState newState) {
        Log.d(TAG, "setConnectionState(): " + connectionState + " -> " + newState);
        connectionState = newState;
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

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
        setConnectionState(ConnectionState.CONNECTING);

        isConnectingOrOpen = true;
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
            startKeepaliveLoop();
                Log.d(TAG, "onOpen(): " + response);
                CallSessionManager.getInstance().onWebSocketConnected();
                setConnectionState(ConnectionState.CONNECTED);
                sendKeepalive();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "onMessage(text): " + text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                Log.d(TAG, "onMessage(binary, len=" + bytes.size() + ")");
                // Forward received PCM to audio playback
                AudioPlaybackStub.enqueuePcm(bytes.toByteArray());
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "onClosing(): code=" + code + " reason=" + reason);
                isConnectingOrOpen = false;
                setConnectionState(ConnectionState.DISCONNECTED);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
            stopKeepaliveLoop();
                Log.d(TAG, "onClosed(): code=" + code + " reason=" + reason);
                CallSessionManager.getInstance().onWebSocketClosed(code, reason);
                isConnectingOrOpen = false;
                setConnectionState(ConnectionState.DISCONNECTED);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
            stopKeepaliveLoop();
                Log.d(TAG, "onFailure(): " + t + " response=" + response, t);
                CallSessionManager.getInstance().onWebSocketError(t);
                isConnectingOrOpen = false;
                setConnectionState(ConnectionState.DISCONNECTED);
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

	long ts = System.currentTimeMillis();
	String payload = "{\"type\":\"CONTROL_HELLO\",\"ts\":" + ts + "}";
	boolean ok = webSocket.send(payload);
	Log.d(TAG, "sendControlHello(): sent text payload, ok=" + ok + " payload=" + payload);
}

    private void startKeepaliveLoop() {
        keepaliveHandler.removeCallbacks(keepaliveRunnable);
        if (connectionState == ConnectionState.CONNECTED) {
            keepaliveHandler.postDelayed(keepaliveRunnable, KEEPALIVE_INTERVAL_MS);
        }
    }

    private void stopKeepaliveLoop() {
        keepaliveHandler.removeCallbacks(keepaliveRunnable);
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

    /**
     * Send raw binary data (PCM audio frames) over WebSocket.
     */
    public synchronized void sendBinary(byte[] data) {
        if (webSocket == null || connectionState != ConnectionState.CONNECTED) {
            return;
        }
        webSocket.send(ByteString.of(data, 0, data.length));
    }

    public void disconnect() {
        stopKeepaliveLoop();        Log.d("GHOSTNET_WS", "disconnect() called");
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Client disconnect");
                Log.d("GHOSTNET_WS", "disconnect(): close() requested");
            } catch (Exception e) {
                Log.d("GHOSTNET_WS", "disconnect(): exception while closing", e);
            } finally {
                webSocket = null;
            }
        }
    }

    public void sendControlBye() {
        Log.d(TAG, "sendControlBye(): called, state=" + connectionState);
        if (connectionState != ConnectionState.CONNECTED) {
            Log.d(TAG, "sendControlBye(): not connected, skipping CONTROL_BYE send");
            return;
        }
        if (webSocket == null) {
            Log.d(TAG, "sendControlBye(): no active websocket");
            return;
        }
        long ts = System.currentTimeMillis();
        String payload = "{\"type\":\"CONTROL_BYE\",\"ts\":" + ts + "}";
        Log.d(TAG, "sendControlBye(): sending CONTROL_BYE, payload=" + payload);
        boolean ok = webSocket.send(payload);
        Log.d(TAG, "sendControlBye(): send() returned " + ok);
    }
}

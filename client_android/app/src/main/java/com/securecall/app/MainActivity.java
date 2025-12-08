package com.securecall.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.securecall.app.ghostnet.media.MediaRouterInboundStub;
import com.securecall.app.ghostnet.transport.ws.GhostNetWebSocketClient;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_UI = "MEDIA_ROUTER_INBOUND";
    private static final String TAG_WS = "GHOSTNET_WS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnCall = findViewById(R.id.btnCall);
        Button btnSettings = findViewById(R.id.btnSettings);

        // --- CALL BUTTON: jetzt mit GhostNet-WS-Connect ---
        btnCall.setOnClickListener(v -> {
            Log.d(TAG_WS, "UI: CALL_CLICK – connecting to GhostNet");

            GhostNetWebSocketClient client = GhostNetWebSocketClient.getInstance();
            client.connect("ws://127.0.0.1:8080");
            client.sendControlHello();

            // Danach wie bisher CallActivity öffnen
            startActivity(new Intent(this, CallActivity.class));
        });

        // --- SETTINGS BUTTON: Debug-Beep + Settings-Screen ---
        btnSettings.setOnClickListener(v -> {
            Log.d(TAG_UI, "UI: SETTINGS_CLICK");

            if (BuildConfig.DEBUG) {
                final int sampleRate = 48000;
                final int durationMs = 250;
                final double freqHz = 440.0;

                int numSamples = sampleRate * durationMs / 1000;
                byte[] pcm = new byte[numSamples * 2]; // 16-bit mono LE

                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / (double) sampleRate;
                    double sample = Math.sin(2.0 * Math.PI * freqHz * t);
                    short s = (short) (sample * 32767.0);

                    int idx = i * 2;
                    pcm[idx] = (byte) (s & 0xFF);
                    pcm[idx + 1] = (byte) ((s >> 8) & 0xFF);
                }

                MediaRouterInboundStub.handleDecodedPcm(pcm);
            }

            startActivity(new Intent(this, SettingsActivity.class));
        });
    }
}

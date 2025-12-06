#!/bin/bash
set -e

echo "== patch_041: guard Settings beep with BuildConfig.DEBUG =="

cat <<'JAVA' > client_android/app/src/main/java/com/securecall/app/MainActivity.java
package com.securecall.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.securecall.app.ghostnet.media.MediaRouterInboundStub;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnCall = findViewById(R.id.btnCall);
        Button btnSettings = findViewById(R.id.btnSettings);

        btnCall.setOnClickListener(v ->
                startActivity(new Intent(this, CallActivity.class)));

        btnSettings.setOnClickListener(v -> {
            // Nur in Debug-Builds den Test-Beep ausführen
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

            // Settings-Screen ganz normal öffnen
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }
}
JAVA

echo "[OK] Wrote MainActivity.java with debug-only beep"
echo "== patch_041 done =="

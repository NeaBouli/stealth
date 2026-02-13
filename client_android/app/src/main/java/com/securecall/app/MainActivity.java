package com.securecall.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.securecall.app.audio.capture.AudioCapturePlaceholder;
import com.securecall.app.ghostnet.media.MediaRouterInboundStub;
import com.securecall.app.ghostnet.transport.ws.GhostNetWebSocketClient;
import com.securecall.app.ghostnet.call.CallSessionManager;

public class MainActivity extends AppCompatActivity {

    private boolean inCall = false;
    private AudioCapturePlaceholder audioCapture;

    private static final String TAG_UI = "MEDIA_ROUTER_INBOUND";
    private static final String TAG_WS = "GHOSTNET_WS";
    private static final int REQUEST_RECORD_AUDIO = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        audioCapture = new AudioCapturePlaceholder();

        Button btnCall = findViewById(R.id.btnCall);
        Button btnSettings = findViewById(R.id.btnSettings);

        // --- CALL BUTTON: WS connect + audio capture ---
        btnCall.setOnClickListener(v -> {
            GhostNetWebSocketClient client = GhostNetWebSocketClient.getInstance();

            if (!inCall) {
                Log.d(TAG_WS, "UI: CALL_CLICK – connecting to GhostNet (enter IN CALL)");
                client.connect("ws://127.0.0.1:8080");
                client.sendControlHello();
                btnCall.setText("IN CALL");
                inCall = true;

                // Request mic permission, then start capture
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    audioCapture.start();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_RECORD_AUDIO);
                }
            } else {
                Log.d(TAG_WS, "UI: CALL_CLICK – sending CONTROL_BYE + disconnect (leave IN CALL)");
                audioCapture.stop();
                client.sendControlBye();
                client.disconnect();
                btnCall.setText("START CALL");
                inCall = false;
            }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG_WS, "RECORD_AUDIO permission granted – starting capture");
                audioCapture.start();
            } else {
                Log.w(TAG_WS, "RECORD_AUDIO permission denied");
                Toast.makeText(this, "Mikrofon-Berechtigung benötigt", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d("GHOSTNET_WS", "MainActivity.onDestroy(): requesting GhostNet disconnect");
        audioCapture.stop();
        GhostNetWebSocketClient.getInstance().disconnect();
        super.onDestroy();
    }

}

package com.securecall.app;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.securecall.app.ghostnet.GhostNetTransport;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";
    private GhostNetTransport transport;
    private boolean isMuted = false;
    private boolean isSpeaker = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        TextView connectionState = findViewById(R.id.connectionState);
        TextView callerNameView = findViewById(R.id.callerName);
        Chronometer callTimer = findViewById(R.id.callTimer);
        FloatingActionButton fabMute = findViewById(R.id.fabMute);
        FloatingActionButton fabEndCall = findViewById(R.id.fabEndCall);
        FloatingActionButton fabSpeaker = findViewById(R.id.fabSpeaker);

        // Handle incoming call from FCM notification
        String callerName = getIntent().getStringExtra("callerName");
        if (callerName != null && !callerName.isEmpty()) {
            callerNameView.setText(callerName);
        }

        if (getIntent().getBooleanExtra("fromNotification", false)) {
            String sessionId = getIntent().getStringExtra("sessionId");
            Log.d(TAG, "Launched from notification: session=" + sessionId + ", caller=" + callerName);

            com.securecall.app.net.WebSocketService ws =
                    com.securecall.app.net.WebSocketService.Companion.getInstance();
            if (ws != null && sessionId != null && !sessionId.isEmpty()) {
                ws.sendCallAccept(sessionId);
            }
        }

        // Start transport
        transport = new GhostNetTransport();
        transport.start();

        // Update connection state
        connectionState.setText(R.string.call_connecting);

        // Start timer after a short delay (simulating connection)
        connectionState.postDelayed(() -> {
            connectionState.setText(R.string.call_active);
            connectionState.setTextColor(getResources().getColor(R.color.call_active_green, getTheme()));
            callTimer.setBase(SystemClock.elapsedRealtime());
            callTimer.setVisibility(View.VISIBLE);
            callTimer.start();
        }, 2000);

        // Mute toggle
        fabMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            fabMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
            fabMute.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            getResources().getColor(
                                    isMuted ? R.color.stealthx_red_dark : R.color.stealthx_gray,
                                    getTheme())));
            fabMute.setContentDescription(getString(isMuted ? R.string.cd_mute : R.string.cd_mute));
        });

        // End call
        fabEndCall.setOnClickListener(v -> {
            callTimer.stop();
            if (transport != null) {
                transport.stop();
            }
            finish();
        });

        // Speaker toggle
        fabSpeaker.setOnClickListener(v -> {
            isSpeaker = !isSpeaker;
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(isSpeaker);
            }
            fabSpeaker.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            getResources().getColor(
                                    isSpeaker ? R.color.stealthx_blue_dark : R.color.stealthx_gray,
                                    getTheme())));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transport != null) {
            transport.stop();
        }
    }
}

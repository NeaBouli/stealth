package com.securecall.app;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.securecall.app.config.FeatureProviderRegistry;
import com.securecall.app.audio.capture.AudioCapturePlaceholder;
import com.securecall.app.security.SecureCallMonitor;

public class CallActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "CallActivity";
    private AudioCapturePlaceholder audioCapture;
    private SecureCallMonitor secureCallMonitor;
    private boolean isMuted = false;
    private boolean isSpeaker = false;
    private boolean isCallActive = false;

    // Proximity sensor
    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private PowerManager.WakeLock proximityWakeLock;

    // Security status UI
    private ImageView securityStatusIcon;
    private TextView securityStatusText;
    private FloatingActionButton fabEndCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Debug: log all intent extras
        Log.d(TAG, "onCreate — intent extras:");
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Log.d(TAG, "  " + key + " = " + extras.get(key));
            }
        } else {
            Log.d(TAG, "  (no extras)");
        }

        // ─── FLAG_SECURE: Prevent screenshots & screen recording ────
        applyFlagSecure();

        setContentView(R.layout.activity_call);

        TextView connectionState = findViewById(R.id.connectionState);
        TextView callerNameView = findViewById(R.id.callerName);
        Chronometer callTimer = findViewById(R.id.callTimer);
        FloatingActionButton fabMute = findViewById(R.id.fabMute);
        fabEndCall = findViewById(R.id.fabEndCall);
        FloatingActionButton fabSpeaker = findViewById(R.id.fabSpeaker);

        // Security status views
        securityStatusIcon = findViewById(R.id.securityStatusIcon);
        securityStatusText = findViewById(R.id.securityStatusText);

        // Handle caller info
        String callerName = getIntent().getStringExtra("callerName");
        String phoneNumber = getIntent().getStringExtra("phoneNumber");
        Log.d(TAG, "Call to: " + callerName + " (" + phoneNumber + ")");

        if (callerName != null && !callerName.isEmpty()) {
            callerNameView.setText(callerName);
        }

        String sessionId = getIntent().getStringExtra("sessionId");
        boolean isIncoming = getIntent().getBooleanExtra("isIncoming", false);
        boolean fromNotification = getIntent().getBooleanExtra("fromNotification", false);

        // ─── Proximity Sensor (screen off at ear) ─────────────────
        initProximitySensor();

        // ─── Initialize Security Monitor ────────────────────────
        initSecurityMonitor();

        com.securecall.app.net.WebSocketService ws =
                com.securecall.app.net.WebSocketService.Companion.getInstance();

        if (fromNotification) {
            // FCM push path — accept and start
            Log.d(TAG, "Launched from notification: session=" + sessionId + ", caller=" + callerName);
            if (ws != null && sessionId != null && !sessionId.isEmpty()) {
                ws.sendCallAccept(sessionId);
            }
            startTransportAndTimer(connectionState, callTimer);
        } else if (isIncoming) {
            // Already accepted from IncomingCallActivity
            Log.d(TAG, "Incoming call accepted: session=" + sessionId);
            startTransportAndTimer(connectionState, callTimer);
        } else {
            // Outgoing call — send CALL_INVITE, wait for CALL_ACCEPT
            String targetId = phoneNumber;
            connectionState.setText(R.string.call_ringing);
            updateCallButton(true);

            if (ws != null && targetId != null && !targetId.isEmpty()) {
                ws.setOnCallAccepted(acceptedSessionId -> {
                    Log.d(TAG, "Remote accepted, session=" + acceptedSessionId);
                    runOnUiThread(() -> startTransportAndTimer(connectionState, callTimer));
                    return kotlin.Unit.INSTANCE;
                });
                ws.setOnCallError((error, message) -> {
                    Log.e(TAG, "Call error: " + error + " — " + message);
                    runOnUiThread(() -> {
                        connectionState.setText("Call failed: " + error);
                        connectionState.setTextColor(getResources().getColor(R.color.stealthx_red, getTheme()));
                        connectionState.postDelayed(this::endCall, 3000);
                    });
                    return kotlin.Unit.INSTANCE;
                });
                ws.sendCallInvite(targetId);
            } else {
                connectionState.setText("Connection error");
            }
        }

        // Listen for remote hangup in all cases
        if (ws != null) {
            ws.setOnCallEnded(endedSessionId -> {
                Log.d(TAG, "Remote ended call, session=" + endedSessionId);
                runOnUiThread(this::endCall);
                return kotlin.Unit.INSTANCE;
            });
        }

        // Mute toggle
        fabMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            fabMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
            fabMute.setBackgroundTintList(
                    ColorStateList.valueOf(
                            getResources().getColor(
                                    isMuted ? R.color.stealthx_red_dark : R.color.stealthx_gray,
                                    getTheme())));
            fabMute.setContentDescription(getString(isMuted ? R.string.cd_mute : R.string.cd_mute));
            // Mute/unmute audio capture
            if (audioCapture != null) {
                if (isMuted) audioCapture.stop();
                else audioCapture.start();
            }
        });

        // End call button — always active (works during ringing, connecting, and active)
        fabEndCall.setOnClickListener(v -> {
            Log.d(TAG, "End call button pressed");
            endCall();
        });

        // Speaker toggle
        fabSpeaker.setOnClickListener(v -> {
            isSpeaker = !isSpeaker;
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(isSpeaker);
            }
            fabSpeaker.setBackgroundTintList(
                    ColorStateList.valueOf(
                            getResources().getColor(
                                    isSpeaker ? R.color.stealthx_blue_dark : R.color.stealthx_gray,
                                    getTheme())));
        });
    }

    /**
     * Update call button color and icon based on call state.
     * Active: RED (end call)
     * Idle/Connecting: GREEN (call in progress)
     */
    private void updateCallButton(boolean active) {
        if (fabEndCall == null) return;
        if (active) {
            fabEndCall.setBackgroundTintList(
                    ColorStateList.valueOf(getResources().getColor(R.color.call_end_red, getTheme())));
            fabEndCall.setImageResource(R.drawable.ic_call_end);
            fabEndCall.setContentDescription(getString(R.string.call_end));
        } else {
            fabEndCall.setBackgroundTintList(
                    ColorStateList.valueOf(getResources().getColor(R.color.call_active_green, getTheme())));
            fabEndCall.setImageResource(R.drawable.ic_call);
            fabEndCall.setContentDescription(getString(R.string.call_start));
        }
    }

    /**
     * Apply FLAG_SECURE based on tier and user preferences.
     */
    private void applyFlagSecure() {
        boolean shouldApply;
        try {
            String tier = FeatureProviderRegistry.INSTANCE.get().getTier();
            if ("PREMIUM".equals(tier)) {
                shouldApply = true;
            } else {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                boolean defaultValue = "PRO".equals(tier);
                shouldApply = prefs.getBoolean("pref_block_screenshots", defaultValue);
            }
        } catch (Exception e) {
            shouldApply = true;
        }

        if (shouldApply) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
            );
            Log.d(TAG, "FLAG_SECURE applied — screenshots and screen recording blocked");
        }
    }

    /**
     * Initialize and start the SecureCallMonitor.
     */
    private void initSecurityMonitor() {
        secureCallMonitor = new SecureCallMonitor(this);

        secureCallMonitor.setOnSecurityStatusChanged(status -> {
            runOnUiThread(() -> updateSecurityUI(status));
            return kotlin.Unit.INSTANCE;
        });

        secureCallMonitor.setOnCriticalThreat(threat -> {
            runOnUiThread(() -> handleCriticalThreat(threat));
            return kotlin.Unit.INSTANCE;
        });

        secureCallMonitor.startContinuousMonitoring(this);
    }

    /**
     * Update the security status indicator in the UI.
     */
    private void updateSecurityUI(SecureCallMonitor.SecurityStatus status) {
        if (securityStatusIcon == null || securityStatusText == null) return;

        switch (status.getLevel()) {
            case GREEN:
                securityStatusIcon.setImageResource(R.drawable.ic_lock);
                securityStatusIcon.setColorFilter(getResources().getColor(R.color.call_active_green, getTheme()));
                securityStatusText.setText(R.string.security_status_secure);
                securityStatusText.setTextColor(getResources().getColor(R.color.call_active_green, getTheme()));
                break;
            case YELLOW:
                securityStatusIcon.setImageResource(R.drawable.ic_shield);
                securityStatusIcon.setColorFilter(getResources().getColor(R.color.stealthx_yellow, getTheme()));
                securityStatusText.setText(getString(R.string.security_status_warning,
                        status.getThreatCount()));
                securityStatusText.setTextColor(getResources().getColor(R.color.stealthx_yellow, getTheme()));
                break;
            case RED:
                securityStatusIcon.setImageResource(R.drawable.ic_shield);
                securityStatusIcon.setColorFilter(getResources().getColor(R.color.stealthx_red_dark, getTheme()));
                securityStatusText.setText(getString(R.string.security_status_critical,
                        status.getThreatCount()));
                securityStatusText.setTextColor(getResources().getColor(R.color.stealthx_red_dark, getTheme()));
                break;
        }
    }

    /**
     * Handle critical security threats based on tier.
     */
    private void handleCriticalThreat(SecureCallMonitor.Threat threat) {
        String tier;
        try {
            tier = FeatureProviderRegistry.INSTANCE.get().getTier();
        } catch (Exception e) {
            tier = "FREE";
        }

        switch (tier) {
            case "FREE":
                Toast.makeText(this,
                        getString(R.string.security_warning_recording, threat.getDescription()),
                        Toast.LENGTH_LONG).show();
                break;

            case "PRO":
                new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                        .setTitle(R.string.security_threat_title)
                        .setMessage(getString(R.string.security_threat_message, threat.getDescription()))
                        .setIcon(R.drawable.ic_shield)
                        .setPositiveButton(R.string.security_end_call, (d, w) -> endCall())
                        .setNegativeButton(R.string.security_continue_call, null)
                        .setCancelable(false)
                        .show();
                break;

            case "PREMIUM":
                Log.e(TAG, "PREMIUM: Critical threat — terminating call");
                endCall();
                break;
        }
    }

    /**
     * Initialize proximity sensor to turn screen off when phone is near ear.
     */
    private void initProximitySensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "Proximity sensor registered");
        } else {
            Log.w(TAG, "No proximity sensor available");
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "securecall:proximity");
            Log.d(TAG, "Proximity wake lock created");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_PROXIMITY) return;

        float distance = event.values[0];
        boolean isNear = distance < proximitySensor.getMaximumRange();

        if (isNear && proximityWakeLock != null && !proximityWakeLock.isHeld()) {
            proximityWakeLock.acquire();
            Log.d(TAG, "Proximity: near — screen off");
        } else if (!isNear && proximityWakeLock != null && proximityWakeLock.isHeld()) {
            proximityWakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
            Log.d(TAG, "Proximity: far — screen on");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    private void releaseProximitySensor() {
        if (sensorManager != null && proximitySensor != null) {
            sensorManager.unregisterListener(this);
        }
        if (proximityWakeLock != null && proximityWakeLock.isHeld()) {
            proximityWakeLock.release();
        }
    }

    private void startTransportAndTimer(TextView connectionState, Chronometer callTimer) {
        connectionState.setText(R.string.call_connecting);
        updateCallButton(false);
        connectionState.postDelayed(() -> {
            isCallActive = true;
            connectionState.setText(R.string.call_active);
            connectionState.setTextColor(getResources().getColor(R.color.call_active_green, getTheme()));
            callTimer.setBase(SystemClock.elapsedRealtime());
            callTimer.setVisibility(View.VISIBLE);
            callTimer.start();
            updateCallButton(true);

            // Start audio capture + send
            audioCapture = new AudioCapturePlaceholder();
            audioCapture.start();
        }, 2000);
    }

    private void endCall() {
        Log.d(TAG, "endCall() — stopping call");

        // Send CALL_END signaling and clear callbacks
        com.securecall.app.net.WebSocketService ws =
                com.securecall.app.net.WebSocketService.Companion.getInstance();
        if (ws != null) {
            String sid = ws.getCurrentSessionId();
            if (sid != null && !sid.isEmpty()) {
                ws.sendCallEnd(sid);
            }
            ws.clearSession();
            ws.setOnCallAccepted(null);
            ws.setOnCallEnded(null);
            ws.setOnCallError(null);
        }

        isCallActive = false;

        // Stop audio capture
        if (audioCapture != null) {
            audioCapture.stop();
            audioCapture = null;
        }
        // Stop audio playback
        com.securecall.app.net.WebSocketService wsAudio =
                com.securecall.app.net.WebSocketService.Companion.getInstance();
        if (wsAudio != null) wsAudio.stopAudioPlayback();

        Chronometer callTimer = findViewById(R.id.callTimer);
        if (callTimer != null) {
            callTimer.stop();
        }
        if (secureCallMonitor != null) {
            secureCallMonitor.stopMonitoring(this);
        }
        releaseProximitySensor();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioCapture != null) {
            audioCapture.stop();
            audioCapture = null;
        }
        if (secureCallMonitor != null) {
            secureCallMonitor.stopMonitoring(this);
        }
        releaseProximitySensor();
    }
}

package com.securecall.app;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
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
import com.securecall.app.ghostnet.GhostNetTransport;
import com.securecall.app.security.SecureCallMonitor;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";
    private GhostNetTransport transport;
    private SecureCallMonitor secureCallMonitor;
    private boolean isMuted = false;
    private boolean isSpeaker = false;

    // Security status UI
    private ImageView securityStatusIcon;
    private TextView securityStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ─── FLAG_SECURE: Prevent screenshots & screen recording ────
        applyFlagSecure();

        setContentView(R.layout.activity_call);

        TextView connectionState = findViewById(R.id.connectionState);
        TextView callerNameView = findViewById(R.id.callerName);
        Chronometer callTimer = findViewById(R.id.callTimer);
        FloatingActionButton fabMute = findViewById(R.id.fabMute);
        FloatingActionButton fabEndCall = findViewById(R.id.fabEndCall);
        FloatingActionButton fabSpeaker = findViewById(R.id.fabSpeaker);

        // Security status views
        securityStatusIcon = findViewById(R.id.securityStatusIcon);
        securityStatusText = findViewById(R.id.securityStatusText);

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

        // ─── Initialize Security Monitor ────────────────────────
        initSecurityMonitor();

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
        fabEndCall.setOnClickListener(v -> endCall());

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

    /**
     * Apply FLAG_SECURE based on tier and user preferences.
     *
     * - FREE:    Optional (user can disable in settings)
     * - PRO:     Default ON (user can disable)
     * - PREMIUM: ENFORCED (cannot be disabled)
     */
    private void applyFlagSecure() {
        boolean shouldApply;
        try {
            String tier = FeatureProviderRegistry.INSTANCE.get().getTier();
            if ("PREMIUM".equals(tier)) {
                // PREMIUM: Always enforced, no opt-out
                shouldApply = true;
            } else {
                // FREE/PRO: Check user preference
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                boolean defaultValue = "PRO".equals(tier); // PRO defaults ON, FREE defaults OFF
                shouldApply = prefs.getBoolean("pref_block_screenshots", defaultValue);
            }
        } catch (Exception e) {
            // Fallback: enable FLAG_SECURE
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

        // Set up security status change listener
        secureCallMonitor.setOnSecurityStatusChanged(status -> {
            runOnUiThread(() -> updateSecurityUI(status));
        });

        // Set up critical threat listener
        secureCallMonitor.setOnCriticalThreat(threat -> {
            runOnUiThread(() -> handleCriticalThreat(threat));
        });

        // Start continuous monitoring
        secureCallMonitor.startContinuousMonitoring(this);
    }

    /**
     * Update the security status indicator in the UI.
     *
     * Green lock:  All checks passed
     * Yellow lock: Warnings present
     * Red lock:    Critical security issues
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
     *
     * FREE:    Show warning toast
     * PRO:     Show blocking dialog
     * PREMIUM: Handled by SecurityEnforcer (terminate)
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
                // Show warning toast
                Toast.makeText(this,
                        getString(R.string.security_warning_recording, threat.getDescription()),
                        Toast.LENGTH_LONG).show();
                break;

            case "PRO":
                // Show blocking dialog
                new AlertDialog.Builder(this, R.style.Theme_SecureCall_Dialog)
                        .setTitle(R.string.security_threat_title)
                        .setMessage(getString(R.string.security_threat_message, threat.getDescription()))
                        .setIcon(R.drawable.ic_shield)
                        .setPositiveButton(R.string.security_end_call, (d, w) -> endCall())
                        .setNegativeButton(R.string.security_continue_call, null)
                        .setCancelable(false)
                        .show();
                break;

            case "PREMIUM":
                // PREMIUM: SecurityEnforcer.handle() already terminates the app
                // This is a fallback in case it didn't trigger
                Log.e(TAG, "PREMIUM: Critical threat — terminating call");
                endCall();
                break;
        }
    }

    private void endCall() {
        Chronometer callTimer = findViewById(R.id.callTimer);
        if (callTimer != null) {
            callTimer.stop();
        }
        if (transport != null) {
            transport.stop();
        }
        if (secureCallMonitor != null) {
            secureCallMonitor.stopMonitoring(this);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transport != null) {
            transport.stop();
        }
        if (secureCallMonitor != null) {
            secureCallMonitor.stopMonitoring(this);
        }
    }
}

package com.securecall.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.media.ToneGenerator;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.securecall.app.config.FeatureProviderRegistry;
import com.securecall.app.audio.capture.AudioCapturePlaceholder;
import com.securecall.app.data.CallHistoryRepository;
import com.securecall.app.data.CallRecord;
import com.securecall.app.data.CallType;
import com.securecall.app.security.SecureCallMonitor;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";
    private static final int REQUEST_RECORD_AUDIO = 1001;

    // Static reference for remote audio cleanup (from WebSocketService.killAllAudio)
    private static volatile CallActivity activeInstance;

    /** Stop ringback tone on the active CallActivity from any thread. */
    public static void stopActiveAudio() {
        CallActivity instance = activeInstance;
        if (instance != null) {
            instance.stopRingbackTone();
            Log.d(TAG, "stopActiveAudio() — ringback stopped via static call");
        }
    }

    private AudioCapturePlaceholder audioCapture;
    // Saved refs for starting audio after permission grant
    private TextView pendingConnectionState;
    private Chronometer pendingCallTimer;
    private SecureCallMonitor secureCallMonitor;
    private boolean isMuted = false;
    private boolean isSpeaker = false;
    private boolean isCallActive = false;
    private volatile boolean isEnding = false;
    private volatile boolean showingSaveDialog = false;
    private long callStartTimeMs = 0;
    private boolean isIncomingCall = false;
    private String callContactName = "";
    private String callContactId = "";
    private String originalPhone = "";

    // Proximity wake lock — acquired during call, system auto-manages screen on/off
    private PowerManager.WakeLock proximityWakeLock;

    // Audio focus — prevent other apps from stealing audio
    private AudioManager audioManager;
    private android.media.AudioFocusRequest audioFocusRequest;

    // Phone state monitoring — detect incoming cell calls
    private android.telephony.TelephonyManager telephonyManager;
    private android.telephony.PhoneStateListener phoneStateListener;
    private boolean isPausedForCellCall = false;

    // Ringback tone for caller while waiting
    private ToneGenerator ringbackTone;

    // Security status UI
    private ImageView securityStatusIcon;
    private TextView securityStatusText;
    private TextView securityWarningBanner;
    private FloatingActionButton fabEndCall;
    private SecureCallMonitor.SecurityStatus lastSecurityStatus;
    private final java.util.Set<SecureCallMonitor.ThreatType> shownThreatTypes = new java.util.HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // F-009 FIX — Show the call UI over the lock screen so the user can interact
        // (mute/speaker/hangup) without having to unlock the device first.
        // Required on Android 8.1+ via setShowWhenLocked/setTurnScreenOn APIs, with
        // fallback to Window flags for older versions.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        activeInstance = this;

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

        // BUG-012: Pause ads during active call
        com.securecall.app.ads.AdMobManager.INSTANCE.pauseForCall();

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
        securityWarningBanner = findViewById(R.id.securityWarningBanner);
        findViewById(R.id.securityStatusBar).setOnClickListener(v -> showSecurityDetailsDialog());
        if (securityWarningBanner != null) {
            securityWarningBanner.setOnClickListener(v -> v.setVisibility(View.GONE));
        }

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

        // Track call metadata for history logging
        isIncomingCall = isIncoming || fromNotification;
        callContactId = phoneNumber != null ? phoneNumber : "";
        String origPhone = getIntent().getStringExtra("originalPhone");
        originalPhone = origPhone != null ? origPhone : "";

        // Re-resolve caller name from phone book if it looks like a raw ID or number
        if (callerName == null || callerName.isEmpty() || callerName.startsWith("android-")
                || callerName.matches("^[+\\d\\s\\-()]+$")) {
            String phoneForLookup = !originalPhone.isEmpty() ? originalPhone : callerName;
            String resolved = com.securecall.app.data.PhoneBookResolver.INSTANCE
                    .resolveCallerName(this, callContactId, phoneForLookup != null ? phoneForLookup : "");
            if (resolved != null && !resolved.isEmpty()) {
                callerName = resolved;
                callerNameView.setText(callerName);
            }
        }
        callContactName = (callerName != null && !callerName.isEmpty()) ? callerName : "Unknown";

        // Verified Contact badge — show if caller name was resolved from phone book
        if (callerName != null && !callerName.isEmpty() && !callerName.startsWith("android-")
                && !callerName.matches("^[+\\d\\s\\-()]+$")) {
            connectionState.setText("\u2713 Verified Contact");
            connectionState.setTextColor(getResources().getColor(android.R.color.holo_green_light, getTheme()));
        }

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
            prepareCallAudio(); // BUG-039: pre-configure audio before ICE
            startTransportAndTimer(connectionState, callTimer);
        } else {
            // F-Droid trial check: block outgoing calls if trial expired and still FREE
            if (!com.securecall.app.trial.TrialManager.INSTANCE.isTrialActive(this)
                    && "FREE".equals(com.securecall.app.config.TierManager.INSTANCE.getCurrentTier(this))) {
                connectionState.setText("Trial expired");
                connectionState.setTextColor(getResources().getColor(R.color.stealthx_red, getTheme()));
                com.securecall.app.trial.TrialManager.INSTANCE.showTrialExpiredDialog(this);
                return;
            }
            // Outgoing call — send CALL_INVITE, wait for CALL_ACCEPT
            String targetId = phoneNumber;
            connectionState.setText(R.string.call_ringing);
            updateCallButton(true);
            startRingbackTone();

            // Pre-call connection check: verify WebSocket is connected
            if (ws != null && !ws.isConnected()) {
                Log.w(TAG, "WebSocket not connected — triggering reconnect before call");
                ws.ensureConnected();
                connectionState.setText("Reconnecting\u2026");
            }

            // BUG-039: Pre-configure audio BEFORE ICE to reduce latency
            prepareCallAudio();

            if (ws != null && targetId != null && !targetId.isEmpty()) {
                ws.setOnCallAccepted(acceptedSessionId -> {
                    Log.d(TAG, "Remote accepted, session=" + acceptedSessionId);
                    stopRingbackTone();
                    runOnUiThread(() -> startTransportAndTimer(connectionState, callTimer));
                    return kotlin.Unit.INSTANCE;
                });
                ws.setOnCallError((error, message) -> {
                    Log.e(TAG, "Call error: " + error + " — " + message);
                    stopRingbackTone();
                    // Clear callback to prevent repeated error handling
                    ws.setOnCallError(null);
                    runOnUiThread(() -> {
                        String userMessage;
                        if ("peer_not_found".equals(error)) {
                            userMessage = "Contact is offline or not registered.\nMake sure they have SecureCall installed.";
                        } else if ("busy".equals(error)) {
                            userMessage = "Contact is busy on another call.";
                        } else {
                            userMessage = "Call failed: " + message;
                        }
                        connectionState.setText(userMessage);
                        connectionState.setTextColor(getResources().getColor(R.color.stealthx_red, getTheme()));
                        connectionState.postDelayed(this::endCall, 4000);
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
                // Verify the change actually took effect
                boolean actual = audioManager.isSpeakerphoneOn();
                Log.d(TAG, "Speaker toggle: requested=" + isSpeaker
                        + ", actual=" + actual
                        + ", mode=" + audioManager.getMode());
                // Sync UI to actual state (not requested) in case system refused
                isSpeaker = actual;
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
        com.securecall.app.security.WindowSecurityHelper.applyFlagSecure(this);
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
        lastSecurityStatus = status;

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
     * Show a dialog explaining the current security warnings/threats.
     */
    private void showSecurityDetailsDialog() {
        if (lastSecurityStatus == null || lastSecurityStatus.getThreats().isEmpty()) {
            // GREEN — nothing to show
            Toast.makeText(this, R.string.security_status_secure, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder message = new StringBuilder();
        for (SecureCallMonitor.Threat threat : lastSecurityStatus.getThreats()) {
            String icon = threat.getSeverity() == SecureCallMonitor.Severity.CRITICAL ? "\u26a0" : "\u26ab";
            message.append(icon).append(" ").append(threat.getDescription()).append("\n");
            for (String detail : threat.getDetails()) {
                message.append("    \u2022 ").append(detail).append("\n");
            }
        }

        int titleRes = lastSecurityStatus.getLevel() == SecureCallMonitor.SecurityLevel.RED
                ? R.string.security_threat_title
                : R.string.security_warnings_title;

        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(titleRes)
                .setMessage(message.toString().trim())
                .setIcon(R.drawable.ic_shield)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Handle critical security threats based on tier.
     */
    private void handleCriticalThreat(SecureCallMonitor.Threat threat) {
        // Only show each threat type once per call session
        if (shownThreatTypes.contains(threat.getType())) {
            return;
        }
        shownThreatTypes.add(threat.getType());

        // Audio focus lost — retry before warning
        if (threat.getType() == SecureCallMonitor.ThreatType.AUDIO_FOCUS_LOST) {
            requestAudioFocus();
            // Re-check after retry
            if (secureCallMonitor != null && secureCallMonitor.getAudioFocusManager().hasFocus()) {
                shownThreatTypes.remove(threat.getType()); // Allow re-check if lost again
                return;
            }
        }

        String tier;
        try {
            tier = FeatureProviderRegistry.INSTANCE.get().getTier();
        } catch (Exception e) {
            tier = "FREE";
        }

        switch (tier) {
            case "PREMIUM":
                Log.e(TAG, "PREMIUM: Critical threat — terminating call");
                endCall();
                break;

            default:
                // Show as non-blocking banner with actionable explanation
                showWarningBanner(threat);
                break;
        }
    }

    private void showWarningBanner(SecureCallMonitor.Threat threat) {
        if (securityWarningBanner == null) return;
        String message = getActionableMessage(threat);
        securityWarningBanner.setText(message);
        securityWarningBanner.setVisibility(View.VISIBLE);
        // Tap to dismiss
        securityWarningBanner.setOnClickListener(v -> securityWarningBanner.setVisibility(View.GONE));
        // Auto-hide after 10 seconds
        securityWarningBanner.removeCallbacks(null);
        securityWarningBanner.postDelayed(() -> {
            if (securityWarningBanner != null) {
                securityWarningBanner.setVisibility(View.GONE);
            }
        }, 10000);
    }

    private String getActionableMessage(SecureCallMonitor.Threat threat) {
        switch (threat.getType()) {
            case SCREEN_RECORDING:
                return "\u26a0 Screen recording detected. Turn off screen recording to protect your call.";
            case DISPLAY_CAPTURE:
                return "\u26a0 Display capture active. Close screen mirroring or casting apps.";
            case MICROPHONE_HIJACK:
                return "\u26a0 Another app is using the microphone. Close other voice/recording apps.";
            case SPY_APP_ACCESSIBILITY:
                String apps = threat.getDetails().isEmpty() ? "" : " (" + String.join(", ", threat.getDetails()) + ")";
                return "\u26a0 Suspicious accessibility service" + apps + ". Check Settings > Accessibility and disable unknown services.";
            case SUSPICIOUS_NOTIFICATION_LISTENER:
                return "\u26a0 Suspicious app has notification access. Check Settings > Apps > Special access > Notification access.";
            case CALL_RECORDING_APP:
                String recApps = threat.getDetails().isEmpty() ? "" : ": " + String.join(", ", threat.getDetails());
                return "\u26a0 Call recording app installed" + recApps + ". Uninstall or disable it for secure calls.";
            case AUDIO_FOCUS_LOST:
                return "\u26a0 Another app took audio focus. Close music/video apps for best call quality.";
            default:
                return "\u26a0 " + threat.getDescription();
        }
    }

    /**
     * BUG-030: Configure audio manager for voice call.
     * Sets MODE_IN_COMMUNICATION and raises STREAM_VOICE_CALL to max volume.
     */
    private int savedAudioMode = -1;
    private int savedStreamVolume = -1;

    private volatile boolean audioConfigured = false; // BUG-038: guard against double init

    private void configureCallAudio() {
        if (audioConfigured) {
            Log.w(TAG, "configureCallAudio() already called — skipping duplicate");
            return;
        }
        audioConfigured = true;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        // Save current state for restoration
        savedAudioMode = am.getMode();
        savedStreamVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        // Set communication mode — routes audio through earpiece
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        // Set voice call volume to max
        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0);
        Log.d(TAG, "Audio configured: MODE_IN_COMMUNICATION, volume=" + maxVol + "/" + maxVol);
        com.securecall.app.debug.SecLogManager.INSTANCE.logIfEnabled(this, "AUDIO",
                "Mode=IN_COMMUNICATION, volume=" + maxVol + "/" + maxVol);
    }

    /** BUG-039: Prepare audio BEFORE ICE connects — reduces latency. */
    private void prepareCallAudio() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        // Save original mode BEFORE changing it (BUG-069 fix)
        if (savedAudioMode < 0) {
            savedAudioMode = am.getMode();
            savedStreamVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        }
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0);
        Log.d(TAG, "Audio pre-configured for call (BUG-039), saved original mode=" + savedAudioMode);
    }

    private void restoreCallAudio() {
        audioConfigured = false; // BUG-038: reset guard
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        if (savedAudioMode >= 0) am.setMode(savedAudioMode);
        if (savedStreamVolume >= 0) am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedStreamVolume, 0);
        Log.d(TAG, "Audio restored to previous mode");
    }

    /**
     * Acquire PROXIMITY_SCREEN_OFF_WAKE_LOCK — system automatically turns screen
     * off when proximity sensor detects near, and back on when far.
     */
    private void initProximitySensor() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "securecall:proximity");
            proximityWakeLock.acquire(60 * 60 * 1000L); // 1h max — safety net if endCall() not reached
            Log.d(TAG, "Proximity wake lock acquired (1h timeout)");
        } else {
            Log.w(TAG, "PROXIMITY_SCREEN_OFF_WAKE_LOCK not supported");
        }
    }

    private void releaseProximitySensor() {
        if (proximityWakeLock != null && proximityWakeLock.isHeld()) {
            proximityWakeLock.release();
            Log.d(TAG, "Proximity wake lock released");
        }
    }

    private void startTransportAndTimer(TextView connectionState, Chronometer callTimer) {
        connectionState.setText(R.string.call_connecting);
        updateCallButton(false);
        connectionState.postDelayed(() -> {
            isCallActive = true;
            callStartTimeMs = System.currentTimeMillis();
            com.securecall.app.debug.SecLogManager.INSTANCE.logIfEnabled(CallActivity.this, "CALL", "Call active: " + callContactName);

            // BUG-030: Configure audio for voice call — set mode + max volume
            configureCallAudio();
            connectionState.setText(R.string.call_active);
            connectionState.setTextColor(getResources().getColor(R.color.call_active_green, getTheme()));
            callTimer.setBase(SystemClock.elapsedRealtime());
            callTimer.setVisibility(View.VISIBLE);
            callTimer.start();
            updateCallButton(true);

            // Mark call as active — extends WebSocket staleness threshold
            com.securecall.app.net.WebSocketService wsActive =
                    com.securecall.app.net.WebSocketService.Companion.getInstance();
            if (wsActive != null) wsActive.setCallActive(true);

            // Acquire audio focus to prevent other apps from interrupting
            requestAudioFocus();

            // Monitor for incoming cell phone calls
            startPhoneStateMonitor(connectionState);

            // Start audio capture (requires RECORD_AUDIO permission)
            startAudioCaptureWithPermission(connectionState, callTimer);
        }, 2000);
    }

    private void startAudioCaptureWithPermission(TextView connectionState, Chronometer callTimer) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            audioCapture = new AudioCapturePlaceholder();
            audioCapture.start();
        } else {
            pendingConnectionState = connectionState;
            pendingCallTimer = callTimer;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "RECORD_AUDIO permission granted");
                audioCapture = new AudioCapturePlaceholder();
                audioCapture.start();
            } else {
                Log.w(TAG, "RECORD_AUDIO permission denied");
                Toast.makeText(this, "Microphone permission required for calls", Toast.LENGTH_LONG).show();
            }
            pendingConnectionState = null;
            pendingCallTimer = null;
        }
    }

    private void startRingbackTone() {
        try {
            ringbackTone = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80);
            ringbackTone.startTone(ToneGenerator.TONE_SUP_RINGTONE);
            Log.d(TAG, "Ringback tone started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ringback tone", e);
        }
    }

    private void stopRingbackTone() {
        ToneGenerator tone = ringbackTone;
        ringbackTone = null;
        if (tone != null) {
            try { tone.stopTone(); } catch (Exception e) { Log.e(TAG, "Error stopping ringback tone", e); }
            try { tone.release(); } catch (Exception e) { Log.e(TAG, "Error releasing ringback tone", e); }
            Log.d(TAG, "Ringback tone stopped and released");
        }
    }

    @SuppressWarnings("deprecation")
    private void requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager == null) return;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest = new android.media.AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setOnAudioFocusChangeListener(focusChange -> {
                        Log.d(TAG, "Audio focus changed: " + focusChange);
                        com.securecall.app.net.WebSocketService wsFocus =
                                com.securecall.app.net.WebSocketService.Companion.getInstance();
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            Log.w(TAG, "Audio focus lost — pausing SecureCall audio");
                            if (audioCapture != null && !isMuted) {
                                audioCapture.pause();
                            }
                            if (wsFocus != null) wsFocus.pauseAudioPlayback();
                            isPausedForCellCall = true;
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                            Log.d(TAG, "Audio focus regained — resuming SecureCall audio");
                            if (isPausedForCellCall) {
                                if (audioCapture != null && !isMuted) {
                                    audioCapture.resume();
                                }
                                if (wsFocus != null) wsFocus.resumeAudioPlayback();
                                isPausedForCellCall = false;
                            }
                        }
                    })
                    .build();
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            Log.d(TAG, "Audio focus requested: " + (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "GRANTED" : "DENIED"));
        } else {
            audioManager.requestAudioFocus(
                    focusChange -> Log.d(TAG, "Audio focus changed: " + focusChange),
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
        audioFocusRequest = null;
    }

    @SuppressWarnings("deprecation")
    private void startPhoneStateMonitor(TextView connectionState) {
        telephonyManager = (android.telephony.TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (telephonyManager == null) return;

        phoneStateListener = new android.telephony.PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                com.securecall.app.net.WebSocketService wsInner =
                        com.securecall.app.net.WebSocketService.Companion.getInstance();
                switch (state) {
                    case android.telephony.TelephonyManager.CALL_STATE_RINGING:
                        Log.w(TAG, "Incoming cell call detected — pausing SecureCall audio");
                        runOnUiThread(() -> {
                            if (connectionState != null && isCallActive) {
                                connectionState.setText("Paused — incoming phone call");
                                connectionState.setTextColor(getResources().getColor(R.color.stealthx_red, getTheme()));
                            }
                        });
                        // Pause capture (lightweight — keeps AudioRecord alive)
                        if (audioCapture != null && !isMuted) {
                            audioCapture.pause();
                        }
                        // Pause playback (playout thread writes silence)
                        if (wsInner != null) wsInner.pauseAudioPlayback();
                        isPausedForCellCall = true;
                        break;
                    case android.telephony.TelephonyManager.CALL_STATE_OFFHOOK:
                        Log.w(TAG, "Cell call answered — SecureCall audio remains paused");
                        break;
                    case android.telephony.TelephonyManager.CALL_STATE_IDLE:
                        if (isPausedForCellCall) {
                            Log.d(TAG, "Cell call ended — resuming SecureCall audio");
                            runOnUiThread(() -> {
                                if (connectionState != null && isCallActive) {
                                    connectionState.setText(R.string.call_active);
                                    connectionState.setTextColor(getResources().getColor(R.color.call_active_green, getTheme()));
                                }
                            });
                            // Resume capture (lightweight — restarts recording)
                            if (audioCapture != null && !isMuted) {
                                audioCapture.resume();
                            }
                            // Resume playback
                            if (wsInner != null) wsInner.resumeAudioPlayback();
                            isPausedForCellCall = false;
                        }
                        break;
                }
            }
        };
        try {
            telephonyManager.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE);
            Log.d(TAG, "Phone state monitor started");
        } catch (SecurityException e) {
            // READ_PHONE_STATE not granted — non-critical, call still works without cell-call detection
            Log.w(TAG, "Phone state monitor skipped — READ_PHONE_STATE not granted", e);
            telephonyManager = null;
            phoneStateListener = null;
        }
    }

    private void stopPhoneStateMonitor() {
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE);
            phoneStateListener = null;
            Log.d(TAG, "Phone state monitor stopped");
        }
    }

    private void endCall() {
        if (isEnding) return;
        isEnding = true;
        stopRingbackTone();
        restoreCallAudio(); // BUG-030: restore audio mode + volume
        Log.d(TAG, "endCall() — stopping call");
        com.securecall.app.debug.SecLogManager.INSTANCE.logIfEnabled(this, "CALL", "Call ended: " + callContactName);

        // Send CALL_END signaling and clear callbacks
        // CRITICAL: Clear callbacks BEFORE clearSession() to prevent WebRTC close()
        // from triggering onPeerDisconnect → _onCallEnded while callbacks are still set
        com.securecall.app.net.WebSocketService ws =
                com.securecall.app.net.WebSocketService.Companion.getInstance();
        if (ws != null) {
            String sid = ws.getCurrentSessionId();
            if (sid != null && !sid.isEmpty()) {
                ws.sendCallEnd(sid);
            }
            ws.setOnCallAccepted(null);
            ws.setOnCallEnded(null);
            ws.setOnCallError(null);
            ws.clearSession();
        }

        // Save call to history (only if user hasn't disabled it)
        boolean saveHistory = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("pref_call_history", true);
        if (saveHistory) {
            try {
                int durationSecs = 0;
                if (callStartTimeMs > 0) {
                    durationSecs = (int) ((System.currentTimeMillis() - callStartTimeMs) / 1000);
                }
                CallType callType = isIncomingCall ? CallType.INCOMING : CallType.OUTGOING;
                // BUG-013: Save phoneNumber for future name re-resolution
                String savePhone = !originalPhone.isEmpty() ? originalPhone : null;
                CallRecord record = new CallRecord(
                    java.util.UUID.randomUUID().toString(),
                    callContactName, callContactId, callType,
                    System.currentTimeMillis(), durationSecs, true,
                    savePhone
                );
                CallHistoryRepository.INSTANCE.add(this, record);
                Log.d(TAG, "Call history saved: " + callType + " " + callContactName + " " + durationSecs + "s");
                com.securecall.app.debug.SecLogManager.INSTANCE.logIfEnabled(this, "CALL",
                    "Duration: " + durationSecs + "s, type: " + callType + ", contact: " + callContactName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save call history", e);
            }
        } else {
            Log.d(TAG, "Call history saving disabled by user preference");
        }

        isCallActive = false;

        // Clear call active flag on WebSocket — restores normal staleness threshold
        com.securecall.app.net.WebSocketService wsFlag =
                com.securecall.app.net.WebSocketService.Companion.getInstance();
        if (wsFlag != null) wsFlag.setCallActive(false);

        // Stop phone state monitoring and release audio focus
        stopPhoneStateMonitor();
        abandonAudioFocus();

        // Stop audio capture
        if (audioCapture != null) {
            audioCapture.stop();
            audioCapture = null;
        }
        // Stop all audio playback globally
        com.securecall.app.net.WebSocketService wsAudio =
                com.securecall.app.net.WebSocketService.Companion.getInstance();
        if (wsAudio != null) {
            wsAudio.killAllAudio();
            // Post-call recovery: ensure WebSocket reconnects cleanly
            wsAudio.postCallRecovery();
        }

        Chronometer callTimer = findViewById(R.id.callTimer);
        if (callTimer != null) {
            callTimer.stop();
        }
        if (secureCallMonitor != null) {
            secureCallMonitor.stopMonitoring(this);
        }

        // Show interstitial ad after call — only if effective tier is FREE
        if (com.securecall.app.config.TierManager.INSTANCE.isFreeTier(this)) {
            com.securecall.app.ads.AdMobManager.INSTANCE.onCallCompleted(this);
        }

        // Release proximity sensor BEFORE dialogs so screen turns on after peer hangup.
        // FLAG_KEEP_SCREEN_ON prevents Samsung Activity destruction during dialog.
        releaseProximitySensor();

        if (shouldOfferContactSave()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            showSaveContactDialog();
        } else if (shouldOfferVerify()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            showVerifyDialog();
        } else {
            finish();
        }
    }

    /** BUG-031: Check if we should offer to verify an unverified contact after call. */
    private boolean shouldOfferVerify() {
        if (callContactId.isEmpty()) return false;
        java.util.List<com.securecall.app.data.Contact> contacts =
                com.securecall.app.data.ContactRepository.INSTANCE.getAll(this);
        for (com.securecall.app.data.Contact c : contacts) {
            if ((c.getPhoneOrId().equals(callContactId) ||
                 (c.getSecureId() != null && c.getSecureId().equals(callContactId))) &&
                !c.isVerified()) {
                return true;
            }
        }
        return false;
    }

    private void showVerifyDialog() {
        if (isFinishing() || isDestroyed()) { releaseProximitySensor(); finish(); return; }
        new AlertDialog.Builder(this)
            .setTitle("Verify " + callContactName + "?")
            .setMessage("You just had a call with " + callContactName + ". Verify this contact?")
            .setPositiveButton("Verify \u2713", (d, w) -> {
                java.util.List<com.securecall.app.data.Contact> contacts =
                        com.securecall.app.data.ContactRepository.INSTANCE.getAll(this);
                for (com.securecall.app.data.Contact c : contacts) {
                    if (c.getPhoneOrId().equals(callContactId) ||
                        (c.getSecureId() != null && c.getSecureId().equals(callContactId))) {
                        com.securecall.app.data.Contact verified = c.copy(
                            c.getId(), c.getName(), c.getPhoneOrId(), c.getCreatedAt(),
                            c.isPhoneContact(), c.getSecureId(), true, c.isBlocked());
                        com.securecall.app.data.ContactRepository.INSTANCE.update(this, verified);
                        Toast.makeText(this, callContactName + " verified \u2713", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
                releaseProximitySensor();
                finish();
            })
            .setNegativeButton("Skip", (d, w) -> {
                releaseProximitySensor();
                finish();
            })
            .setCancelable(false)
            .show();
    }

    /** Check if we should offer to save this contact (phone→clientId resolved, not already saved). */
    private boolean shouldOfferContactSave() {
        Log.d(TAG, "shouldOfferContactSave: originalPhone='" + originalPhone
                + "', callContactId='" + callContactId + "'");
        if (originalPhone.isEmpty() || callContactId.isEmpty()) return false;
        if (!callContactId.startsWith("android-")) return false;
        // Check if already saved in contacts (by clientId or phone number)
        String normalizedPhone = com.securecall.app.data.PhoneUtils.INSTANCE.normalize(originalPhone, this);
        java.util.List<com.securecall.app.data.Contact> contacts =
                com.securecall.app.data.ContactRepository.INSTANCE.getAll(this);
        for (com.securecall.app.data.Contact c : contacts) {
            if (c.getPhoneOrId().equals(callContactId)) {
                Log.d(TAG, "shouldOfferContactSave: already saved by clientId");
                return false;
            }
            String cNorm = com.securecall.app.data.PhoneUtils.INSTANCE.normalize(c.getPhoneOrId(), this);
            if (!cNorm.isEmpty() && cNorm.equals(normalizedPhone)) {
                Log.d(TAG, "shouldOfferContactSave: already saved by phone number");
                return false;
            }
        }
        Log.d(TAG, "shouldOfferContactSave: YES — showing dialog");
        return true;
    }

    private void showSaveContactDialog() {
        if (isFinishing() || isDestroyed()) {
            releaseProximitySensor();
            finish();
            return;
        }
        showingSaveDialog = true;
        new AlertDialog.Builder(this)
            .setTitle("Save Contact")
            .setMessage("Save " + callContactName + " (" + originalPhone + ") as a SecureCall contact?\n\nFuture calls will connect directly without phone lookup.")
            .setPositiveButton("Save", (d, w) -> {
                showingSaveDialog = false;
                // Save with phone as primary key and SecureID as metadata —
                // this deduplicates correctly with phone book contacts
                String savePhoneOrId = (originalPhone != null && !originalPhone.isEmpty())
                    ? originalPhone : callContactId;
                String saveSecureId = callContactId.startsWith("android-") ? callContactId : null;
                com.securecall.app.data.Contact contact = new com.securecall.app.data.Contact(
                    java.util.UUID.randomUUID().toString(),
                    callContactName, savePhoneOrId,
                    System.currentTimeMillis(), false, saveSecureId,
                    false, false
                );
                com.securecall.app.data.ContactRepository.INSTANCE.save(this, contact);
                com.securecall.app.ui.ContactsFragment.Companion.invalidateCache();
                Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Saved contact: " + callContactName + " -> " + callContactId);
                releaseProximitySensor();
                finish();
            })
            .setNegativeButton("Skip", (d, w) -> {
                showingSaveDialog = false;
                releaseProximitySensor();
                finish();
            })
            .setCancelable(false)
            .show();
    }

    @Override
    protected void onDestroy() {
        // Clear static instance if WE are the current one
        if (activeInstance == this) {
            activeInstance = null;
        }
        showingSaveDialog = false;
        stopRingbackTone();
        stopPhoneStateMonitor();
        abandonAudioFocus();
        if (audioCapture != null) {
            try { audioCapture.stop(); } catch (Exception e) { Log.e(TAG, "Error stopping audio capture", e); }
            audioCapture = null;
        }
        // Belt-and-suspenders: kill all audio globally
        com.securecall.app.net.WebSocketService ws =
                com.securecall.app.net.WebSocketService.Companion.getInstance();
        if (ws != null) {
            try { ws.killAllAudio(); } catch (Exception e) { Log.e(TAG, "Error in killAllAudio", e); }
        }
        if (secureCallMonitor != null) {
            secureCallMonitor.stopMonitoring(this);
        }
        releaseProximitySensor();
        super.onDestroy();
    }
}

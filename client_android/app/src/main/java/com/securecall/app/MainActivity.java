package com.securecall.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import android.view.View;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import com.securecall.app.audio.capture.AudioCapturePlaceholder;
import com.securecall.app.ghostnet.transport.ws.GhostNetWebSocketClient;
import com.securecall.app.security.SecurityEnforcer;
import com.securecall.app.init.AppInit;
import com.securecall.app.fcm.FcmTokenManager;
import com.securecall.app.ui.CallsFragment;
import com.securecall.app.ui.ContactsFragment;
import com.securecall.app.ui.DialerFragment;
import com.securecall.app.ui.SettingsFragment;
import com.securecall.app.ui.onboarding.OnboardingActivity;

public class MainActivity extends AppCompatActivity {

    private boolean inCall = false;
    private AudioCapturePlaceholder audioCapture;
    private MaterialToolbar toolbar;

    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final int REQUEST_POST_NOTIFICATIONS = 1002;
    private static final int REQUEST_PHONE_PERMISSION = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Splash screen (must be before super.onCreate)
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // Initialize flavor-specific FeatureProvider
        AppInit.INSTANCE.init(this);

        // Security checks at startup
        runSecurityChecks();

        // Register FCM token for push notifications
        FcmTokenManager.INSTANCE.ensureTokenRegistered(this);

        // Request POST_NOTIFICATIONS permission on Android 13+ (required for foreground service)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_POST_NOTIFICATIONS);
            }
        }

        // Start WebSocket signaling service as foreground service (survives background)
        Intent wsIntent = new Intent(this, com.securecall.app.net.WebSocketService.class);
        androidx.core.content.ContextCompat.startForegroundService(this, wsIntent);

        // Request phone number permission for server registration
        requestPhoneNumberPermission();

        // Check if onboarding needed
        SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("onboarding_complete", false)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        audioCapture = new AudioCapturePlaceholder();

        // Setup toolbar with connection status subtitle
        toolbar = findViewById(R.id.topAppBar);
        toolbar.setSubtitle("Connecting\u2026");
        toolbar.setSubtitleTextColor(getResources().getColor(android.R.color.darker_gray, null));
        wireConnectionStatusCallbacks();
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                showFragment(new SettingsFragment());
                BottomNavigationView nav = findViewById(R.id.bottomNav);
                nav.setSelectedItemId(R.id.nav_settings);
                return true;
            }
            return false;
        });

        // Setup bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNewCall);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calls) {
                showFragment(new CallsFragment());
                fab.setVisibility(View.VISIBLE);
                // Mark calls as seen — clear badge and dismiss notifications
                getSharedPreferences("securecall_prefs", MODE_PRIVATE).edit()
                        .putLong("last_calls_viewed", System.currentTimeMillis()).apply();
                bottomNav.removeBadge(R.id.nav_calls);
                clearMissedCallNotifications();
                return true;
            } else if (id == R.id.nav_contacts) {
                showFragment(new ContactsFragment());
                fab.setVisibility(View.VISIBLE);
                return true;
            } else if (id == R.id.nav_dialer) {
                showFragment(new DialerFragment());
                // Hide fabNewCall so the dialer's own fabCall is accessible
                fab.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_settings) {
                showFragment(new SettingsFragment());
                fab.setVisibility(View.GONE);
                return true;
            }
            return false;
        });

        // FAB for new call — navigate to contacts to pick a recipient
        fab.setOnClickListener(v -> {
            Log.d(TAG, "New Call FAB clicked — navigating to contacts");
            bottomNav.setSelectedItemId(R.id.nav_contacts);
        });

        // Default fragment
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_calls);
        }
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
    }

    private void handleCallToggle(ExtendedFloatingActionButton fab) {
        GhostNetWebSocketClient client = GhostNetWebSocketClient.getInstance();

        if (!inCall) {
            Log.d(TAG, "Starting call — connecting to GhostNet");
            client.connect(BuildConfig.SIGNAL_WS_URL);
            client.sendControlHello();
            fab.setText(R.string.call_active);
            fab.setIconResource(R.drawable.ic_call_end);
            inCall = true;

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                audioCapture.start();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_AUDIO);
            }
        } else {
            Log.d(TAG, "Ending call — disconnecting from GhostNet");
            audioCapture.stop();
            client.sendControlBye();
            client.disconnect();
            fab.setText(R.string.new_call);
            fab.setIconResource(R.drawable.ic_call);
            inCall = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                audioCapture.start();
            } else {
                Toast.makeText(this, R.string.permission_mic_required, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_PHONE_PERMISSION) {
            // Re-register with server (with or without phone number)
            checkAndPromptPhoneNumber();
        }
    }

    private void requestPhoneNumberPermission() {
        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            permission = Manifest.permission.READ_PHONE_NUMBERS;
        } else {
            permission = Manifest.permission.READ_PHONE_STATE;
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_PHONE_PERMISSION);
        } else {
            checkAndPromptPhoneNumber();
        }
    }

    private void checkAndPromptPhoneNumber() {
        // Wait briefly for WebSocket service to connect
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
            // Already confirmed? Re-register and skip
            String confirmed = prefs.getString("confirmed_phone_number", null);
            if (confirmed != null && !confirmed.trim().isEmpty()) {
                com.securecall.app.net.WebSocketService ws =
                        com.securecall.app.net.WebSocketService.Companion.getInstance();
                if (ws != null) ws.reRegister();
                return;
            }
            // First launch — show confirm dialog with SIM suggestion
            if (isFinishing() || isDestroyed()) return;
            String simSuggestion = readSimNumber();
            promptForPhoneNumber(prefs, simSuggestion);
        }, 3000);
    }

    /** Read phone number from SIM as a suggestion (may be wrong on some carriers). */
    @android.annotation.SuppressLint("MissingPermission")
    private String readSimNumber() {
        try {
            String permission;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                permission = Manifest.permission.READ_PHONE_NUMBERS;
            } else {
                permission = Manifest.permission.READ_PHONE_STATE;
            }
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            android.telephony.TelephonyManager tm =
                    (android.telephony.TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            String num = tm.getLine1Number();
            if (num != null && !num.trim().isEmpty()) return num.trim();
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private void promptForPhoneNumber(SharedPreferences prefs, String simSuggestion) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        if (simSuggestion != null) {
            input.setText(simSuggestion);
            input.setSelection(simSuggestion.length());
        } else {
            input.setHint("+49...");
        }

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Your Phone Number")
                .setMessage("Please verify your phone number. Others will use this number to call you on SecureCall.")
                .setView(container)
                .setPositiveButton("Confirm", (d, w) -> {
                    String number = input.getText().toString().trim();
                    if (!number.isEmpty()) {
                        prefs.edit()
                                .putString("confirmed_phone_number", number)
                                .apply();
                        Log.d(TAG, "Phone number confirmed: " + number);
                        com.securecall.app.net.WebSocketService ws =
                                com.securecall.app.net.WebSocketService.Companion.getInstance();
                        if (ws != null) ws.reRegister();
                    }
                })
                .setNegativeButton("Skip", (d, w) -> {
                    // No number confirmed — register without phone
                    com.securecall.app.net.WebSocketService ws =
                            com.securecall.app.net.WebSocketService.Companion.getInstance();
                    if (ws != null) ws.reRegister();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkMissedCallBadge();
    }

    private void checkMissedCallBadge() {
        SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
        long lastViewed = prefs.getLong("last_calls_viewed", 0);
        int newMissed = com.securecall.app.data.CallHistoryRepository.INSTANCE
                .countMissedSince(this, lastViewed);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav == null) return;
        if (newMissed > 0) {
            bottomNav.getOrCreateBadge(R.id.nav_calls).setNumber(newMissed);
            // Show snackbar only if not already on the Calls tab
            if (bottomNav.getSelectedItemId() != R.id.nav_calls) {
                View root = findViewById(R.id.nav_host_fragment);
                String msg = newMissed == 1
                        ? "1 missed call"
                        : newMissed + " missed calls";
                Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
                        .setAction("View", v -> bottomNav.setSelectedItemId(R.id.nav_calls))
                        .show();
            } else {
                // Already on Calls tab — mark as seen
                prefs.edit().putLong("last_calls_viewed", System.currentTimeMillis()).apply();
                bottomNav.removeBadge(R.id.nav_calls);
                clearMissedCallNotifications();
            }
        } else {
            bottomNav.removeBadge(R.id.nav_calls);
        }
    }

    private void clearMissedCallNotifications() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
        String activeIds = prefs.getString("missed_notif_ids", "");
        if (activeIds != null && !activeIds.isEmpty()) {
            for (String idStr : activeIds.split(",")) {
                try {
                    nm.cancel(Integer.parseInt(idStr.trim()));
                } catch (NumberFormatException ignored) {}
            }
            prefs.edit().putString("missed_notif_ids", "").apply();
        }
        nm.cancel(1003); // Group summary
    }

    private void wireConnectionStatusCallbacks() {
        com.securecall.app.net.WebSocketService ws =
                com.securecall.app.net.WebSocketService.Companion.getInstance();
        if (ws != null) {
            ws.setStatusCallbackOnline(() -> {
                runOnUiThread(() -> {
                    if (toolbar != null) {
                        toolbar.setSubtitle("\u25CF Connected");
                        toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_active_green, null));
                    }
                });
                return kotlin.Unit.INSTANCE;
            });
            ws.setStatusCallbackOffline(() -> {
                runOnUiThread(() -> {
                    if (toolbar != null) {
                        toolbar.setSubtitle("\u25CF Disconnected");
                        toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_end_red, null));
                    }
                });
                return kotlin.Unit.INSTANCE;
            });
            // Set initial state
            if (ws.isConnected()) {
                toolbar.setSubtitle("\u25CF Connected");
                toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_active_green, null));
            } else {
                toolbar.setSubtitle("\u25CF Disconnected");
                toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_end_red, null));
            }
        } else {
            // Service not started yet — retry after a short delay
            new android.os.Handler(getMainLooper()).postDelayed(this::wireConnectionStatusCallbacks, 1000);
        }
    }

    private void runSecurityChecks() {
        if (isRooted()) {
            SecurityEnforcer.INSTANCE.handle(SecurityEnforcer.Violation.ROOT_DETECTED);
        }
        if (isEmulator()) {
            SecurityEnforcer.INSTANCE.handle(SecurityEnforcer.Violation.EMULATOR_DETECTED);
        }
        if (android.os.Debug.isDebuggerConnected()) {
            SecurityEnforcer.INSTANCE.handle(SecurityEnforcer.Violation.DEBUGGER_ATTACHED);
        }
    }

    private boolean isRooted() {
        String[] paths = {"/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
                "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"};
        for (String path : paths) {
            if (new java.io.File(path).exists()) return true;
        }
        return false;
    }

    private boolean isEmulator() {
        return android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.BRAND.startsWith("generic")
                || android.os.Build.DEVICE.startsWith("generic")
                || "google_sdk".equals(android.os.Build.PRODUCT);
    }

    @Override
    protected void onDestroy() {
        if (audioCapture != null) {
            audioCapture.stop();
        }
        GhostNetWebSocketClient.getInstance().disconnect();
        super.onDestroy();
    }
}

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

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
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
            com.securecall.app.net.WebSocketService ws =
                    com.securecall.app.net.WebSocketService.Companion.getInstance();
            if (ws != null) {
                ws.reRegister();
            }
            // Check if a phone number is available (manual or carrier)
            SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
            String manual = prefs.getString("manual_phone_number", null);
            if (manual != null && !manual.trim().isEmpty()) return;
            // Check carrier number
            try {
                if (hasPhonePermission()) {
                    android.telephony.TelephonyManager tm =
                            (android.telephony.TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                    @android.annotation.SuppressLint("MissingPermission")
                    String num = tm.getLine1Number();
                    if (num != null && !num.trim().isEmpty()) return; // Carrier provides it
                }
            } catch (Exception e) { /* ignore */ }
            // No phone number available — prompt user once per install
            if (prefs.getBoolean("phone_number_prompted", false)) return;
            promptForPhoneNumber(prefs);
        }, 3000);
    }

    private boolean hasPhonePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void promptForPhoneNumber(SharedPreferences prefs) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        input.setHint("+49...");

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Enter Your Phone Number")
                .setMessage("Your carrier doesn't provide your phone number automatically. Enter it so others can call you by phone number.")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String number = input.getText().toString().trim();
                    if (!number.isEmpty()) {
                        prefs.edit()
                                .putString("manual_phone_number", number)
                                .putBoolean("phone_number_prompted", true)
                                .apply();
                        Log.d(TAG, "Manual phone number saved: " + number);
                        // Re-register with server
                        com.securecall.app.net.WebSocketService ws2 =
                                com.securecall.app.net.WebSocketService.Companion.getInstance();
                        if (ws2 != null) ws2.reRegister();
                    }
                })
                .setNegativeButton("Skip", (d, w) -> {
                    prefs.edit().putBoolean("phone_number_prompted", true).apply();
                })
                .setCancelable(false)
                .show();
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

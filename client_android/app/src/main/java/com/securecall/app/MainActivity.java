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
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calls) {
                showFragment(new CallsFragment());
                return true;
            } else if (id == R.id.nav_contacts) {
                showFragment(new ContactsFragment());
                return true;
            } else if (id == R.id.nav_dialer) {
                showFragment(new DialerFragment());
                return true;
            } else if (id == R.id.nav_settings) {
                showFragment(new SettingsFragment());
                return true;
            }
            return false;
        });

        // FAB for new call — navigate to contacts to pick a recipient
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNewCall);
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

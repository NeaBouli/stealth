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

        // Apply activated tier override (activation code / IFR lock unlock)
        com.securecall.app.config.TierManager.INSTANCE.applyTier(this);

        // FLAG_SECURE: prevent screenshots based on tier/preference
        applyFlagSecure();
        // Re-verify IFR lock if due (every 24h)
        com.securecall.app.config.IfrLockManager.INSTANCE.reverifyIfNeeded(this);

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

        // WebSocket service is started in SecureCallApplication.onCreate() (before any Activity).
        // On Android 8 (Galaxy S7), starting from Activity is too late — the 5-second
        // startForeground() window expires before the service's onCreate() runs.
        // This is now just a safety net in case the Application path didn't fire.
        if (com.securecall.app.net.WebSocketService.Companion.getInstance() == null) {
            Intent wsIntent = new Intent(this, com.securecall.app.net.WebSocketService.class);
            androidx.core.content.ContextCompat.startForegroundService(this, wsIntent);
        }

        // FIX 7: Request battery optimization exemption for reliable call reception
        requestBatteryOptimizationExemption();

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
            } else if (item.getItemId() == R.id.action_connection) {
                // BUG-015: Disconnect/Reconnect toggle
                handleConnectionToggle();
                return true;
            }
            return false;
        });

        // Setup bottom navigation with system bar insets (TB-005: Samsung navbar overlap fix)
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
            int navBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBarHeight);
            return insets;
        });
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNewCall);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calls) {
                showFragment(new CallsFragment());
                fab.setVisibility(View.GONE); // BUG-017: FAB only in Dialer tab
                // Mark calls as seen — clear badge and dismiss notifications
                getSharedPreferences("securecall_prefs", MODE_PRIVATE).edit()
                        .putLong("last_calls_viewed", System.currentTimeMillis()).apply();
                bottomNav.removeBadge(R.id.nav_calls);
                clearMissedCallNotifications();
                return true;
            } else if (id == R.id.nav_contacts) {
                showFragment(new ContactsFragment());
                fab.setVisibility(View.GONE); // BUG-017: FAB only in Dialer tab
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

        // AdMob — only show ads if effective tier is FREE (TB-013: ensure container visible)
        android.widget.FrameLayout adContainer = findViewById(R.id.adBannerContainer);
        if (com.securecall.app.config.TierManager.INSTANCE.isFreeTier(this)) {
            if (adContainer != null) adContainer.setVisibility(android.view.View.VISIBLE);
            com.securecall.app.ads.AdMobManager.INSTANCE.init(this);
            com.securecall.app.ads.AdMobManager.INSTANCE.loadBanner(this, adContainer);
            com.securecall.app.ads.AdMobManager.INSTANCE.preloadInterstitial(this);
        } else {
            // Upgraded user — hide ad container completely
            if (adContainer != null) {
                com.securecall.app.ads.AdMobManager.INSTANCE.destroyBanner(adContainer);
                adContainer.setVisibility(android.view.View.GONE);
            }
            android.util.Log.d("AdMob", "Ads disabled — tier: " + com.securecall.app.config.TierManager.INSTANCE.getCurrentTier(this));
        }

        // Handle invite deep link: stealthx.tech/invite/{secureId}
        handleInviteDeepLink(getIntent());
        // Handle custom-id deep link: securecall://custom-id?id=xxx&token=xxx
        handleCustomIdDeepLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleInviteDeepLink(intent);
        handleCustomIdDeepLink(intent);
    }

    private void handleInviteDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        android.net.Uri data = intent.getData();

        // Handle securecall://add-contact?id=xxx&name=xxx OR https://stealthx.tech/invite/{id}
        String id = null;
        if ("securecall".equals(data.getScheme()) && "add-contact".equals(data.getHost())) {
            id = data.getQueryParameter("id");
        }
        java.util.List<String> segments = data.getPathSegments();
        if (id == null && segments != null && segments.size() >= 2 && "invite".equals(segments.get(0))) {
            id = segments.get(1);
        }
        if (id == null || id.isEmpty()) return;
        final String inviterSecureId = id;

        // Read optional name parameter
        String nameParam = data.getQueryParameter("name");
        final String displayName = (nameParam != null && !nameParam.isEmpty()) ? nameParam : inviterSecureId;

        Log.d(TAG, "Invite deep link received: " + inviterSecureId + " name=" + displayName);

        // Show confirmation dialog before saving
        new android.app.AlertDialog.Builder(this)
            .setTitle("\uD83D\uDD12 Add Contact")
            .setMessage("Add " + displayName + " as a SecureCall contact?\n\nID: " + inviterSecureId)
            .setPositiveButton("Add Contact", (d, w) -> {
                // Save inviter as a contact
                SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
                java.util.Set<String> contacts = new java.util.HashSet<>(
                        prefs.getStringSet("saved_contacts", new java.util.HashSet<>()));
                if (!contacts.contains(inviterSecureId)) {
                    contacts.add(inviterSecureId);
                    prefs.edit().putStringSet("saved_contacts", contacts).apply();
                    Log.d(TAG, "Inviter saved as contact: " + inviterSecureId);
                }
                android.widget.Toast.makeText(this, displayName + " added!", android.widget.Toast.LENGTH_SHORT).show();

                // Notify backend
                notifyInviteAccepted(inviterSecureId, prefs.getString("client_id", ""));
            })
            .setNeutralButton("Call Now", (d, w) -> {
                // Save + call immediately
                SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
                java.util.Set<String> contacts = new java.util.HashSet<>(
                        prefs.getStringSet("saved_contacts", new java.util.HashSet<>()));
                contacts.add(inviterSecureId);
                prefs.edit().putStringSet("saved_contacts", contacts).apply();
                notifyInviteAccepted(inviterSecureId, prefs.getString("client_id", ""));

                Intent callIntent = new Intent(this, CallActivity.class);
                callIntent.putExtra("phoneNumber", inviterSecureId);
                callIntent.putExtra("callerName", displayName);
                startActivity(callIntent);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();

        // Clear the intent data so it doesn't re-trigger
        intent.setData(null);
    }

    private void notifyInviteAccepted(String inviterSecureId, String mySecureId) {
        if (inviterSecureId.isEmpty() || mySecureId.isEmpty()) return;
        new Thread(() -> {
            try {
                String serverUrl = com.securecall.app.BuildConfig.SIGNAL_WS_URL
                        .replace("wss://", "https://").replace("ws://", "http://").replace("/signal", "");
                String json = "{\"inviterSecureId\":\"" + inviterSecureId + "\",\"newUserSecureId\":\"" + mySecureId + "\"}";
                okhttp3.RequestBody body = okhttp3.RequestBody.create(json, okhttp3.MediaType.parse("application/json"));
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(serverUrl + "/invite/accepted")
                        .post(body).build();
                new okhttp3.OkHttpClient().newCall(request).execute();
                Log.d(TAG, "Invite accepted notification sent");
            } catch (Exception e) {
                Log.w(TAG, "Failed to notify invite accepted: " + e.getMessage());
            }
        }).start();
    }

    private void handleCustomIdDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        android.net.Uri data = intent.getData();
        if (!"securecall".equals(data.getScheme()) || !"custom-id".equals(data.getHost())) return;

        String customId = data.getQueryParameter("id");
        String token = data.getQueryParameter("token");
        if (customId == null || customId.isEmpty() || token == null || token.isEmpty()) return;

        Log.d(TAG, "Custom ID deep link received: id=" + customId);

        // Verify token + activate custom ID via backend
        activateCustomId(customId, token);
        intent.setData(null);
    }

    private void activateCustomId(String customId, String token) {
        SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
        String deviceId = prefs.getString("client_id", "");
        if (deviceId.isEmpty()) {
            android.widget.Toast.makeText(this, "Device not registered yet. Please wait.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            try {
                String serverUrl = com.securecall.app.BuildConfig.SIGNAL_WS_URL
                        .replace("wss://", "https://").replace("ws://", "http://").replace("/signal", "");
                String json = "{\"id\":\"" + customId + "\",\"deviceId\":\"" + deviceId + "\",\"token\":\"" + token + "\"}";
                okhttp3.RequestBody body = okhttp3.RequestBody.create(json, okhttp3.MediaType.parse("application/json"));
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(serverUrl + "/custom-id/activate-token")
                        .post(body).build();
                okhttp3.Response response = new okhttp3.OkHttpClient().newCall(request).execute();
                String respBody = response.body() != null ? response.body().string() : "";

                runOnUiThread(() -> {
                    if (response.isSuccessful() && respBody.contains("\"success\":true")) {
                        prefs.edit().putString("custom_call_id", customId).apply();
                        new android.app.AlertDialog.Builder(this)
                            .setTitle("Custom ID Activated")
                            .setMessage("Your Custom Call ID \"" + customId + "\" is now active!\n\nOthers can call you using this ID.")
                            .setPositiveButton("OK", (d, w) -> recreate())
                            .setCancelable(false)
                            .show();
                    } else {
                        android.widget.Toast.makeText(this, "Activation failed: " + respBody, android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                    android.widget.Toast.makeText(this, "Connection error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    /** BUG-015: Disconnect/Reconnect toggle from toolbar button. */
    private void handleConnectionToggle() {
        com.securecall.app.net.WebSocketService ws =
                com.securecall.app.net.WebSocketService.Companion.getInstance();
        if (ws == null) {
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ws.isConnected()) {
            // Connected → show disconnect confirmation
            new android.app.AlertDialog.Builder(this)
                .setTitle("Disconnect")
                .setMessage("Disconnect from SecureCall server?\nYou won't receive calls while disconnected.")
                .setPositiveButton("Disconnect", (d, w) -> {
                    ws.manualDisconnect();
                    toolbar.setSubtitle("\u25CF Disconnected");
                    toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_end_red, null));
                    Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        } else {
            // Disconnected → reconnect
            ws.forceReconnect();
            toolbar.setSubtitle("Reconnecting\u2026");
            toolbar.setSubtitleTextColor(getResources().getColor(android.R.color.darker_gray, null));
            tintConnectionButton(0xFFFFC107); // yellow during reconnect
            Toast.makeText(this, "Reconnecting\u2026", Toast.LENGTH_SHORT).show();
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

    /** Apply FLAG_SECURE to prevent screenshots based on tier and toggle. */
    private void applyFlagSecure() {
        try {
            String tier = com.securecall.app.config.TierManager.INSTANCE.getCurrentTier(this);
            boolean isPremium = "PREMIUM".equals(tier);
            boolean isPro = "PRO".equals(tier);
            boolean isFree = !isPremium && !isPro;

            // Free: never apply FLAG_SECURE
            if (isFree) {
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                return;
            }

            // Premium: always apply
            if (isPremium) {
                getWindow().setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
                return;
            }

            // Pro: follow toggle
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            boolean blockScreenshots = prefs.getBoolean("pref_block_screenshots", true);
            if (blockScreenshots) {
                getWindow().setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Exception e) {
            android.util.Log.w("MainActivity", "Failed to apply FLAG_SECURE", e);
        }
    }

    /**
     * BUG-027: Request battery optimization exemption.
     * Re-asks every 7 days if user hasn't granted it yet.
     * Critical for Samsung A-series which aggressively kill background services.
     */
    @android.annotation.SuppressLint("BatteryLife")
    private void requestBatteryOptimizationExemption() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
                long lastAsked = prefs.getLong("battery_opt_last_asked", 0);
                long now = System.currentTimeMillis();
                // Re-ask every 7 days if not granted
                if (now - lastAsked > 7 * 24 * 60 * 60 * 1000L) {
                    prefs.edit().putLong("battery_opt_last_asked", now).apply();
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        android.util.Log.w("MainActivity", "Battery optimization request failed", e);
                    }
                }
            }
            // Samsung-specific: try to open Samsung's own battery settings
            openSamsungBatterySettings();
        }
    }

    /** BUG-027: Samsung has extra battery restrictions beyond standard Android. */
    private void openSamsungBatterySettings() {
        SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("samsung_battery_shown", false)) return;
        // Only on Samsung devices
        if (!android.os.Build.MANUFACTURER.equalsIgnoreCase("samsung")) return;
        // Show once — explain to user
        prefs.edit().putBoolean("samsung_battery_shown", true).apply();
        new android.app.AlertDialog.Builder(this)
            .setTitle("Samsung Battery Optimization")
            .setMessage("Samsung may stop SecureCall from running in the background.\n\n"
                + "To receive calls reliably:\n"
                + "Settings → Apps → SecureCall → Battery → Unrestricted\n\n"
                + "Open battery settings now?")
            .setPositiveButton("Open Settings", (d, w) -> {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    android.util.Log.w("MainActivity", "Failed to open app settings", e);
                }
            })
            .setNegativeButton("Later", null)
            .show();
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
                        // Normalize phone number before saving (BUG-025)
                        String normalized = com.securecall.app.data.PhoneUtils.INSTANCE.normalize(number, MainActivity.this);
                        prefs.edit()
                                .putString("confirmed_phone_number", normalized)
                                .apply();
                        Log.d(TAG, "Phone number confirmed: " + number + " -> normalized: " + normalized);
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
        // Always clear launcher badge (notification-based) when user opens the app
        clearMissedCallNotifications();
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
                    tintConnectionButton(0xFF4CAF50); // green
                });
                return kotlin.Unit.INSTANCE;
            });
            ws.setStatusCallbackOffline(() -> {
                runOnUiThread(() -> {
                    if (toolbar != null) {
                        toolbar.setSubtitle("\u25CF Disconnected");
                        toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_end_red, null));
                    }
                    tintConnectionButton(0xFF9E9E9E); // gray
                });
                return kotlin.Unit.INSTANCE;
            });
            // Set initial state
            if (ws.isConnected()) {
                toolbar.setSubtitle("\u25CF Connected");
                toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_active_green, null));
                tintConnectionButton(0xFF4CAF50);
            } else {
                toolbar.setSubtitle("\u25CF Disconnected");
                toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_end_red, null));
                tintConnectionButton(0xFF9E9E9E);
            }
        } else {
            // Service not started yet — retry after a short delay
            new android.os.Handler(getMainLooper()).postDelayed(this::wireConnectionStatusCallbacks, 1000);
        }
    }

    /** Tint the connection toolbar button to reflect current status. */
    private void tintConnectionButton(int color) {
        if (toolbar == null) return;
        android.view.Menu menu = toolbar.getMenu();
        if (menu == null) return;
        android.view.MenuItem item = menu.findItem(R.id.action_connection);
        if (item != null && item.getIcon() != null) {
            item.getIcon().setTint(color);
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

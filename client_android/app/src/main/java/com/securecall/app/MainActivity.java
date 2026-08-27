package com.securecall.app;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import com.securecall.app.data.Contact;
import com.securecall.app.data.ContactRepository;
import com.securecall.app.ghostnet.transport.ws.GhostNetWebSocketClient;
import com.securecall.app.security.SecurityEnforcer;
import com.securecall.app.init.AppInit;
import com.securecall.app.fcm.FcmTokenManager;
import com.securecall.app.net.ExternalVpnMonitor;
import com.securecall.app.ui.CallsFragment;
import com.securecall.app.ui.ContactsFragment;
import com.securecall.app.ui.DialerFragment;
import com.securecall.app.ui.EdgeToEdgeHelper;
import com.securecall.app.ui.SettingsFragment;
import com.securecall.app.ui.onboarding.OnboardingActivity;

public class MainActivity extends AppCompatActivity {

    private boolean inCall = false;
    private AudioCapturePlaceholder audioCapture;
    private MaterialToolbar toolbar;
    private ExternalVpnMonitor externalVpnMonitor;
    private int wireRetryCount = 0;
    private androidx.appcompat.app.AlertDialog phoneNumberDialog;

    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final int REQUEST_POST_NOTIFICATIONS = 1002;
    private static final int REQUEST_PHONE_PERMISSION = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Splash screen (must be before super.onCreate)
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);

        // Initialize flavor-specific FeatureProvider
        AppInit.INSTANCE.init(this);

        // Apply activated tier override (activation code unlock)
        com.securecall.app.config.TierManager.INSTANCE.applyTier(this);

        // FLAG_SECURE: prevent screenshots based on tier/preference
        applyFlagSecure();

        // Security checks at startup
        runSecurityChecks();

        // On a fresh install, hand off to onboarding before starting permission,
        // service, or dialog flows. Otherwise MainActivity can leak windows while
        // it immediately finishes after launching OnboardingActivity.
        SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("onboarding_complete", false)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

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

        // On Android 14+, USE_FULL_SCREEN_INTENT requires explicit user grant via Settings.
        // Without it, incoming call screen never surfaces automatically.
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (!nm.canUseFullScreenIntent()) {
                Intent intent = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENTS",
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }

        // WebSocket service is started in SecureCallApplication.onCreate() (before any Activity).
        // On Android 8 (Galaxy S7), starting from Activity is too late — the 5-second
        // startForeground() window expires before the service's onCreate() runs.
        // This is now just a safety net in case the Application path didn't fire.
        startWebSocketServiceIfNeeded();

        // FIX 7: Request battery optimization exemption for reliable call reception
        requestBatteryOptimizationExemption();

        // Request phone number permission for server registration
        requestPhoneNumberPermission();

        setContentView(R.layout.activity_main);
        EdgeToEdgeHelper.applyTopSystemBarPadding(findViewById(R.id.appBarLayout));

        audioCapture = new AudioCapturePlaceholder();

        // Setup toolbar with connection status subtitle
        toolbar = findViewById(R.id.topAppBar);
        toolbar.setSubtitle("Connecting\u2026");
        toolbar.setSubtitleTextColor(getResources().getColor(android.R.color.darker_gray, null));
        wireConnectionStatusCallbacks();

        // VPN transport status LED: visible only while this process routes through
        // an Android system VPN. Flavor-specific copy distinguishes external Play
        // routing from the direct Premium APK tunnel.
        android.view.MenuItem vpnStatusItem = toolbar.getMenu().findItem(R.id.action_external_vpn_status);
        if (vpnStatusItem != null) {
            View vpnActionView = vpnStatusItem.getActionView();
            if (vpnActionView instanceof com.securecall.app.ui.VpnStatusIndicatorView) {
                final com.securecall.app.ui.VpnStatusIndicatorView vpnLed =
                        (com.securecall.app.ui.VpnStatusIndicatorView) vpnActionView;
                externalVpnMonitor = new ExternalVpnMonitor(this, this, active -> {
                    vpnLed.setVpnActive(active);
                    return kotlin.Unit.INSTANCE;
                });
            }
        }

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
        final int bottomNavInitialLeft = bottomNav.getPaddingLeft();
        final int bottomNavInitialTop = bottomNav.getPaddingTop();
        final int bottomNavInitialRight = bottomNav.getPaddingRight();
        final int bottomNavInitialBottom = bottomNav.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(
                    bottomNavInitialLeft + bars.left,
                    bottomNavInitialTop,
                    bottomNavInitialRight + bars.right,
                    bottomNavInitialBottom + bars.bottom
            );
            updateContentBottomInset();
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(bottomNav);
        android.widget.FrameLayout adContainer = findViewById(R.id.adBannerContainer);
        View navHost = findViewById(R.id.nav_host_fragment);
        View.OnLayoutChangeListener bottomInsetUpdater = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateContentBottomInset();
        bottomNav.addOnLayoutChangeListener(bottomInsetUpdater);
        if (adContainer != null) {
            adContainer.addOnLayoutChangeListener(bottomInsetUpdater);
        }
        if (navHost != null) {
            navHost.post(this::updateContentBottomInset);
        }
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNewCall);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calls) {
                updateAdVisibilityForTab(true);
                showFragment(new CallsFragment());
                fab.setVisibility(View.GONE); // BUG-017: FAB only in Dialer tab
                // Mark calls as seen — clear badge and dismiss notifications
                getSharedPreferences("securecall_prefs", MODE_PRIVATE).edit()
                        .putLong("last_calls_viewed", System.currentTimeMillis()).apply();
                bottomNav.removeBadge(R.id.nav_calls);
                clearMissedCallNotifications();
                return true;
            } else if (id == R.id.nav_contacts) {
                updateAdVisibilityForTab(true);
                showFragment(new ContactsFragment());
                fab.setVisibility(View.GONE); // BUG-017: FAB only in Dialer tab
                return true;
            } else if (id == R.id.nav_dialer) {
                updateAdVisibilityForTab(true);
                showFragment(new DialerFragment());
                // Hide fabNewCall so the dialer's own fabCall is accessible
                fab.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_settings) {
                updateAdVisibilityForTab(false);
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
        if (com.securecall.app.config.TierManager.INSTANCE.isFreeTier(this)) {
            if (adContainer != null) adContainer.setVisibility(android.view.View.VISIBLE);
            com.securecall.app.ads.AdMobManager.INSTANCE.requestConsentAndLoad(this, adContainer);
            updateAdVisibilityForTab(bottomNav.getSelectedItemId() != R.id.nav_settings);
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
        // Trial: show expired dialog at app start if applicable
        if (com.securecall.app.trial.TrialManager.INSTANCE.shouldShowExpiredDialog(this)) {
            com.securecall.app.trial.TrialManager.INSTANCE.showTrialExpiredDialog(this);
        }

        // Throttled background check for new releases — silent if up-to-date.
        // Only fires for sideload / unknown-store installs; Play Store handles its own.
        com.securecall.app.update.UpdateManager.maybeAutoCheck(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInviteDeepLink(intent);
        handleCustomIdDeepLink(intent);
    }

    private void handleInviteDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        android.net.Uri data = intent.getData();

        // Handle securecall://add-contact?id=xxx&name=xxx,
        // https://stealthx.tech/invite/?id=xxx, or legacy https://stealthx.tech/invite/{id}.
        String id = data.getQueryParameter("id");
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
                // Save inviter as a contact in the active contact repository.
                SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
                ContactRepository.SaveResult saveResult = ContactRepository.INSTANCE.save(this, new Contact(
                        java.util.UUID.randomUUID().toString(),
                        displayName,
                        inviterSecureId,
                        System.currentTimeMillis(),
                        false,
                        null,
                        false,
                        false
                ));
                if (saveResult == ContactRepository.SaveResult.REJECTED_CONTACT_LIMIT) {
                    android.widget.Toast.makeText(this,
                            com.securecall.app.config.TierLimitPolicy.INSTANCE.contactLimitMessage(
                                    com.securecall.app.config.FeatureProviderRegistry.INSTANCE.get().getMaxContacts()),
                            android.widget.Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Invite contact save rejected by Free-tier limit: " + inviterSecureId);
                    return;
                }
                ContactsFragment.Companion.invalidateCache();
                Log.d(TAG, "Inviter saved as contact: " + inviterSecureId);
                android.widget.Toast.makeText(this, displayName + " added!", android.widget.Toast.LENGTH_SHORT).show();

                // Notify backend
                notifyInviteAccepted(inviterSecureId, prefs.getString("client_id", ""));
            })
            .setNeutralButton("Call Now", (d, w) -> {
                // Save + call immediately. The call itself is not contact-gated:
                // if the Free-tier contact limit rejects the save, the call still proceeds.
                SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
                ContactRepository.SaveResult saveResult = ContactRepository.INSTANCE.save(this, new Contact(
                        java.util.UUID.randomUUID().toString(),
                        displayName,
                        inviterSecureId,
                        System.currentTimeMillis(),
                        false,
                        null,
                        false,
                        false
                ));
                if (saveResult == ContactRepository.SaveResult.REJECTED_CONTACT_LIMIT) {
                    android.widget.Toast.makeText(this,
                            com.securecall.app.config.TierLimitPolicy.INSTANCE.contactLimitMessage(
                                    com.securecall.app.config.FeatureProviderRegistry.INSTANCE.get().getMaxContacts()),
                            android.widget.Toast.LENGTH_LONG).show();
                } else {
                    ContactsFragment.Companion.invalidateCache();
                }
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
                com.securecall.app.net.NetworkManager.INSTANCE.buildPinnedClient().newCall(request).execute();
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
                okhttp3.Response response = com.securecall.app.net.NetworkManager.INSTANCE.buildPinnedClient().newCall(request).execute();
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
            startWebSocketServiceIfNeeded();
            toolbar.setSubtitle("Reconnecting\u2026");
            toolbar.setSubtitleTextColor(getResources().getColor(android.R.color.darker_gray, null));
            tintConnectionButton(0xFFFFC107);
            wireRetryCount = 0;
            wireConnectionStatusCallbacks();
            Toast.makeText(this, "Reconnecting\u2026", Toast.LENGTH_SHORT).show();
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

    private void startWebSocketServiceIfNeeded() {
        if (com.securecall.app.net.WebSocketService.Companion.getInstance() != null) return;
        try {
            Intent wsIntent = new Intent(this, com.securecall.app.net.WebSocketService.class);
            androidx.core.content.ContextCompat.startForegroundService(this, wsIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WebSocketService", e);
            Toast.makeText(this, "Connection service could not start", Toast.LENGTH_LONG).show();
        }
    }

    private void updateAdVisibilityForTab(boolean allowAds) {
        android.widget.FrameLayout adContainer = findViewById(R.id.adBannerContainer);
        if (adContainer == null) return;
        boolean showAds = allowAds && com.securecall.app.config.TierManager.INSTANCE.isFreeTier(this);
        adContainer.setVisibility(showAds ? android.view.View.VISIBLE : android.view.View.GONE);
        adContainer.post(this::updateContentBottomInset);
    }

    private void updateContentBottomInset() {
        View navHost = findViewById(R.id.nav_host_fragment);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        android.widget.FrameLayout adContainer = findViewById(R.id.adBannerContainer);
        if (navHost == null || bottomNav == null) return;

        int bottomNavHeight = bottomNav.getHeight();
        if (adContainer != null) {
            android.view.ViewGroup.LayoutParams rawAdParams = adContainer.getLayoutParams();
            if (rawAdParams instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams adParams =
                        (android.view.ViewGroup.MarginLayoutParams) rawAdParams;
                if (adParams.bottomMargin != bottomNavHeight) {
                    adParams.bottomMargin = bottomNavHeight;
                    adContainer.setLayoutParams(adParams);
                }
            }
        }

        int bottomInset = bottomNavHeight;
        if (adContainer != null && adContainer.getVisibility() == View.VISIBLE) {
            bottomInset += adContainer.getHeight();
        }

        android.view.ViewGroup.LayoutParams rawParams = navHost.getLayoutParams();
        if (rawParams instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams params =
                    (android.view.ViewGroup.MarginLayoutParams) rawParams;
            if (params.bottomMargin != bottomInset) {
                params.bottomMargin = bottomInset;
                navHost.setLayoutParams(params);
            }
        }
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
        com.securecall.app.security.WindowSecurityHelper.applyFlagSecure(this);
    }

    /**
     * BUG-027: Request battery optimization exemption.
     * Shows an explaining dialog BEFORE the system dialog so the user understands why.
     * Re-asks every 7 days if user hasn't granted it yet.
     * Critical for Samsung A-series which aggressively kill background services.
     */
    @android.annotation.SuppressLint("BatteryLife")
    private void requestBatteryOptimizationExemption() {
        if (!com.securecall.app.net.ForegroundServicePolicy
                .shouldRequestBatteryOptimizationExemption(android.os.Build.VERSION.SDK_INT)) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                SharedPreferences prefs = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
                long lastAsked = prefs.getLong("battery_opt_last_asked", 0);
                long now = System.currentTimeMillis();
                // Re-ask every 7 days if not granted
                if (now - lastAsked > 7 * 24 * 60 * 60 * 1000L) {
                    prefs.edit().putLong("battery_opt_last_asked", now).apply();
                    // Show explaining dialog BEFORE system dialog.
                    // Avoid stacking Samsung settings on top of the system exemption flow.
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("Background Connection Required")
                        .setMessage("SecureCall needs to stay connected in the background "
                            + "to receive incoming calls reliably.\n\n"
                            + "Please allow unrestricted battery usage on the next screen. "
                            + "Without this, you may miss calls when your phone is locked or idle.")
                        .setPositiveButton("Allow", (d, w) -> {
                            launchBatteryExemptionIntent();
                        })
                        .setNegativeButton("Later", (d, w) -> {
                            // Still show Samsung hint even if user skips system dialog
                            openSamsungBatterySettings();
                        })
                        .setCancelable(true)
                        .show();
                    return; // Skip Samsung dialog below for this start cycle.
                }
            }
            // Samsung-specific: only reached if battery opt is already granted or re-ask cooldown active
            openSamsungBatterySettings();
        }
    }

    /** Launch the system battery optimization exemption intent. */
    @android.annotation.SuppressLint("BatteryLife")
    private void launchBatteryExemptionIntent() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.w("MainActivity", "Battery optimization request failed — opening app settings", e);
            // Fallback: open app details settings
            try {
                Intent fallback = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(fallback);
            } catch (Exception e2) {
                android.util.Log.w("MainActivity", "Fallback settings also failed", e2);
            }
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
            if (prefs.getBoolean("phone_number_skipped", false)) {
                com.securecall.app.net.WebSocketService ws =
                        com.securecall.app.net.WebSocketService.Companion.getInstance();
                if (ws != null) ws.reRegister();
                return;
            }
            if (prefs.getBoolean("phone_number_prompt_completed", false)) {
                com.securecall.app.net.WebSocketService ws =
                        com.securecall.app.net.WebSocketService.Companion.getInstance();
                if (ws != null) ws.reRegister();
                return;
            }
            // First launch — show confirm dialog with SIM suggestion
            if (isFinishing() || isDestroyed()) return;
            if (phoneNumberDialog != null && phoneNumberDialog.isShowing()) return;
            prefs.edit().putBoolean("phone_number_prompt_completed", true).apply();
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

        phoneNumberDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Your Phone Number")
                .setMessage("Please verify your phone number. Others will use this number to call you on SecureCall.")
                .setView(container)
                .setPositiveButton("Confirm", (d, w) -> {
                    String number = input.getText().toString().trim();
                    if (!number.isEmpty()) {
                        String confirmed = number;
                        try {
                            // Normalize phone number before saving (BUG-025)
                            String normalized = com.securecall.app.data.PhoneUtils.INSTANCE.normalize(number, MainActivity.this);
                            if (normalized != null && !normalized.trim().isEmpty()) {
                                confirmed = normalized.trim();
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Phone number normalization failed; storing raw input", e);
                        }
                        boolean saved = prefs.edit()
                                .putString("manual_phone_number", number)
                                .putString("confirmed_phone_number", confirmed)
                                .putBoolean("phone_number_skipped", false)
                                .putBoolean("phone_number_prompt_completed", true)
                                .commit();
                        if (!saved) {
                            prefs.edit()
                                    .putString("manual_phone_number", number)
                                    .putString("confirmed_phone_number", confirmed)
                                    .putBoolean("phone_number_skipped", false)
                                    .putBoolean("phone_number_prompt_completed", true)
                                    .apply();
                        }
                        Log.d(TAG, "Phone number confirmed: " + number + " -> stored: " + confirmed + " saved=" + saved);
                        com.securecall.app.net.WebSocketService ws =
                                com.securecall.app.net.WebSocketService.Companion.getInstance();
                        if (ws != null) ws.reRegister();
                    } else {
                        prefs.edit()
                                .putBoolean("phone_number_skipped", true)
                                .putBoolean("phone_number_prompt_completed", true)
                                .commit();
                        com.securecall.app.net.WebSocketService ws =
                                com.securecall.app.net.WebSocketService.Companion.getInstance();
                        if (ws != null) ws.reRegister();
                    }
                    phoneNumberDialog = null;
                })
                .setNegativeButton("Skip", (d, w) -> {
                    // No number confirmed — register without phone
                    prefs.edit()
                            .putBoolean("phone_number_skipped", true)
                            .putBoolean("phone_number_prompt_completed", true)
                            .commit();
                    com.securecall.app.net.WebSocketService ws =
                            com.securecall.app.net.WebSocketService.Companion.getInstance();
                    if (ws != null) ws.reRegister();
                    phoneNumberDialog = null;
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Runtime unlocks (activation code) can be applied
        // while the app is already alive, so re-apply the effective tier before
        // refreshing UI, ads, and feature gates.
        com.securecall.app.config.TierManager.INSTANCE.applyTier(this);
        checkMissedCallBadge();
        // Always clear launcher badge (notification-based) when user opens the app
        clearMissedCallNotifications();
        // Trial banner
        updateTrialBanner();
        // Fix CLIENT-CRIT-002 (2026-04-16): ask the server whether our stored
        // subscription tier is still valid. Covers the case where Google Play
        // / Stripe revoked a subscription while the app was offline, so the
        // app did not receive a push. Runs at most once per 6h on resume.
        maybeVerifySubscription();
    }

    private void maybeVerifySubscription() {
        try {
            com.securecall.app.billing.SubscriptionManager sm =
                new com.securecall.app.billing.SubscriptionManager(this);
            if (sm.getCurrentTier() == com.securecall.app.billing.SubscriptionTier.FREE) return;
            long last = sm.getLastVerifiedAt();
            long sixHours = 6L * 60L * 60L * 1000L;
            if (System.currentTimeMillis() - last < sixHours) return;
            SharedPreferences p = getSharedPreferences("securecall_prefs", MODE_PRIVATE);
            String clientId = p.getString("client_id", null);
            if (clientId == null || clientId.isEmpty()) return;
            new Thread(() -> {
                try { sm.verifyAgainstServer(clientId); }
                catch (Exception e) { Log.w(TAG, "subscription verify failed: " + e.getMessage()); }
            }, "subscription-verify").start();
        } catch (Throwable t) {
            Log.w(TAG, "maybeVerifySubscription: " + t.getMessage());
        }
    }

    private void updateTrialBanner() {
        if (toolbar == null) return;
        long remaining = com.securecall.app.trial.TrialManager.INSTANCE.getDaysRemaining(this);
        boolean trialActive = com.securecall.app.trial.TrialManager.INSTANCE.isTrialActive(this);
        String tier = com.securecall.app.config.TierManager.INSTANCE.getCurrentTier(this);
        if ("FREE".equals(tier) && trialActive && remaining <= 30) {
            String title = "StealthX";
            if (remaining <= 5) {
                title = "StealthX  \u26A0\uFE0F Trial: " + remaining + "d left";
            } else if (remaining < 30) {
                title = "StealthX  \u23F3 Trial: " + remaining + "d";
            }
            toolbar.setTitle(title);
        }
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
            // Service not started yet — retry after a short delay (max 10 retries)
            wireRetryCount++;
            if (wireRetryCount <= 10 && !isDestroyed()) {
                new android.os.Handler(getMainLooper()).postDelayed(this::wireConnectionStatusCallbacks, 1000);
            } else if (toolbar != null) {
                toolbar.setSubtitle("\u25CF Disconnected");
                toolbar.setSubtitleTextColor(getResources().getColor(R.color.call_end_red, null));
                tintConnectionButton(0xFF9E9E9E);
            }
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

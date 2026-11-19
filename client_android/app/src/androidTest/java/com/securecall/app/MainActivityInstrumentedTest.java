package com.securecall.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class MainActivityInstrumentedTest {

    // SC242 failures-only diagnostic: closed phase/class metadata for exactly three fixed cases.
    // Opt-in only; never reads messages, stacks, UI text, hierarchy or app data.
    private enum Phase { SETUP, LAUNCH, CLICK, CHECK, CLOSE }

    private static final List<String> FAILURE_METADATA_METHODS = Arrays.asList(
            "externalVpnIndicator_matchesCurrentTransport",
            "settingsNavigationItem_click_doesNotCrash",
            "bottomNavigation_isDisplayed");
    private static final Pattern SIMPLE_CLASS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern SOURCE_ID = Pattern.compile("[0-9a-f]{40}");
    private static final int CAUSE_SLOTS = 4;

    private volatile Phase phase = Phase.SETUP;

    @Rule
    public final TestWatcher failureMetadata = new TestWatcher() {
        @Override
        protected void failed(Throwable failure, Description description) {
            try {
                reportFailureMetadata(failure, description);
            } catch (Throwable diagnosticFailure) {
                // Diagnostics must never alter the original failure or cleanup result.
            }
        }
    };

    private void reportFailureMetadata(Throwable failure, Description description) {
        Bundle args = InstrumentationRegistry.getArguments();
        String head = args.getString("securecallMainFailureHead", "");
        String tree = args.getString("securecallMainFailureTree", "");
        String method = description.getMethodName();
        if (!"true".equals(args.getString("securecallMainFailureMetadata"))
                || !SOURCE_ID.matcher(head).matches() || !SOURCE_ID.matcher(tree).matches()
                || !BuildConfig.DEBUG || !"free".equals(BuildConfig.FLAVOR)
                || Build.VERSION.SDK_INT != 24
                || !("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE))
                || !MainActivityInstrumentedTest.class.getName().equals(description.getClassName())
                || !FAILURE_METADATA_METHODS.contains(method)) {
            return;
        }
        Bundle payload = new Bundle();
        payload.putString("sc242_main_head", head);
        payload.putString("sc242_main_tree", tree);
        payload.putString("sc242_main_method", method);
        payload.putString("sc242_main_phase", phase.name());
        payload.putString("sc242_main_exception_class", simpleClass(failure));
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        seen.put(failure, Boolean.TRUE);
        Throwable cause = failure.getCause();
        int count = 0;
        while (cause != null && count < CAUSE_SLOTS && !seen.containsKey(cause)) {
            seen.put(cause, Boolean.TRUE);
            payload.putString("sc242_main_cause_" + count + "_class", simpleClass(cause));
            count++;
            cause = cause.getCause();
        }
        payload.putString("sc242_main_cause_count", Integer.toString(count));
        payload.putString("sc242_main_cause_incomplete",
                Boolean.toString(cause != null && !seen.containsKey(cause)));
        InstrumentationRegistry.getInstrumentation().sendStatus(2, payload);
    }

    private static String simpleClass(Throwable value) {
        String name = value.getClass().getSimpleName();
        return SIMPLE_CLASS.matcher(name).matches() ? name : "UNKNOWN";
    }

    @Before
    public void grantRuntimePermissions() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantRuntimePermission(context, Manifest.permission.RECORD_AUDIO);
        grantRuntimePermission(context, Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grantRuntimePermission(context, Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void grantRuntimePermission(Context context, String permission) throws IOException {
        String command = "pm grant " + context.getPackageName() + " " + permission;
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
        try (FileInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            byte[] buffer = new byte[256];
            while (stream.read(buffer) != -1) {
                // Draining the pipe waits for the shell command to finish.
            }
        }
    }

    private ActivityScenario<MainActivity> launchMainActivity() {
        phase = Phase.LAUNCH;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean prefsReady = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_complete", true)
                .putBoolean("samsung_battery_shown", true)
                .putLong("battery_opt_last_asked", Long.MAX_VALUE)
                .putString("confirmed_phone_number", "+490000000000")
                .commit();
        assertTrue(prefsReady);
        return ActivityScenario.launch(MainActivity.class);
    }

    @Test
    public void bottomNavigation_isDisplayed() {
        try (ActivityScenario<MainActivity> ignored = launchMainActivity()) {
            phase = Phase.CHECK;
            onView(withId(R.id.bottomNav)).check(matches(isDisplayed()));
            phase = Phase.CLOSE;
        }
    }

    @Test
    public void toolbar_isDisplayed() {
        try (ActivityScenario<MainActivity> ignored = launchMainActivity()) {
            onView(withId(R.id.topAppBar)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void callsNavigationItem_isDisplayed() {
        try (ActivityScenario<MainActivity> ignored = launchMainActivity()) {
            onView(withId(R.id.nav_calls)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void settingsNavigationItem_isDisplayed() {
        try (ActivityScenario<MainActivity> ignored = launchMainActivity()) {
            onView(withId(R.id.nav_settings)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void settingsNavigationItem_click_doesNotCrash() {
        try (ActivityScenario<MainActivity> ignored = launchMainActivity()) {
            phase = Phase.CLICK;
            onView(withId(R.id.nav_settings)).perform(click());
            phase = Phase.CHECK;
            onView(withId(R.id.bottomNav)).check(matches(isDisplayed()));
            phase = Phase.CLOSE;
        }
    }

    @Test
    public void externalVpnIndicator_matchesCurrentTransport() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean vpnActive = com.securecall.app.net.NetworkManager.INSTANCE
                .isExternalVpnActive(context);
        try (ActivityScenario<MainActivity> ignored = launchMainActivity()) {
            phase = Phase.CHECK;
            onView(withId(R.id.vpnStatusIndicator)).check(
                    matches(vpnActive ? isDisplayed() : not(isDisplayed())));
            phase = Phase.CLOSE;
        }
    }
}

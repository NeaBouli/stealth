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
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class MainActivityInstrumentedTest {

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
            onView(withId(R.id.bottomNav)).check(matches(isDisplayed()));
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
            onView(withId(R.id.nav_settings)).perform(click());
            onView(withId(R.id.bottomNav)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void externalVpnIndicator_matchesCurrentTransport() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean vpnActive = com.securecall.app.net.NetworkManager.INSTANCE
                .isExternalVpnActive(context);
        try (ActivityScenario<MainActivity> ignored = launchMainActivity()) {
            onView(withId(R.id.vpnStatusIndicator)).check(
                    matches(vpnActive ? isDisplayed() : not(isDisplayed())));
        }
    }
}

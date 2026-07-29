package com.securecall.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityInstrumentedTest {

    @Before
    public void grantRuntimePermissions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(
                            context.getPackageName(),
                            Manifest.permission.POST_NOTIFICATIONS
                    );
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
}

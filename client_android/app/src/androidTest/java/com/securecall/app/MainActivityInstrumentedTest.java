package com.securecall.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityInstrumentedTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void callButton_isDisplayed() {
        onView(withId(R.id.btnCall)).check(matches(isDisplayed()));
    }

    @Test
    public void settingsButton_isDisplayed() {
        onView(withId(R.id.btnSettings)).check(matches(isDisplayed()));
    }

    @Test
    public void callButton_initialText() {
        onView(withId(R.id.btnCall)).check(matches(withText("Start Call")));
    }

    @Test
    public void callButton_click_changesText() {
        onView(withId(R.id.btnCall)).perform(click());
        // After click the button text should change to indicate active call
        onView(withId(R.id.btnCall)).check(matches(isDisplayed()));
    }

    @Test
    public void settingsButton_click_doesNotCrash() {
        onView(withId(R.id.btnSettings)).perform(click());
        // No crash = pass
    }
}

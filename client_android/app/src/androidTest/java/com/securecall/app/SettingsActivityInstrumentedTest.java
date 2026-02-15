package com.securecall.app;

import static org.junit.Assert.assertNotNull;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityInstrumentedTest {

    @Test
    public void activity_launches_successfully() {
        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                SettingsActivity.class
        );
        ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(intent);
        assertNotNull(scenario);
        scenario.close();
    }
}

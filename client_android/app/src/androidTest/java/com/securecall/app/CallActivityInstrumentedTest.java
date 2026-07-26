package com.securecall.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CallActivityInstrumentedTest {

    private Intent incomingCallIntent() {
        return new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                CallActivity.class
        )
                .putExtra("isIncoming", true)
                .putExtra("sessionId", "instrumented-audio-test")
                .putExtra("callerName", "QA")
                .putExtra("phoneNumber", "android-qa");
    }

    @Test
    public void activity_launches_successfully() {
        ActivityScenario<CallActivity> scenario = ActivityScenario.launch(incomingCallIntent());
        assertNotNull(scenario);
        scenario.close();
    }

    @Test
    public void speaker_selection_survives_delayed_call_activation() {
        ActivityScenario<CallActivity> scenario = ActivityScenario.launch(incomingCallIntent());
        scenario.onActivity(activity -> activity.<View>findViewById(R.id.fabSpeaker).performClick());

        SystemClock.sleep(2500);

        scenario.onActivity(activity -> {
            AudioManager audioManager =
                    (AudioManager) activity.getSystemService(CallActivity.AUDIO_SERVICE);
            assertNotNull(audioManager);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AudioDeviceInfo device = audioManager.getCommunicationDevice();
                assertNotNull(device);
                assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, device.getType());
            } else {
                assertTrue(audioManager.isSpeakerphoneOn());
            }
            assertTrue(activity.<View>findViewById(R.id.fabSpeaker).isSelected());
        });
        scenario.close();
    }

    @Test
    public void hangup_restores_original_audio_mode() {
        AudioManager audioManager = (AudioManager) InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext()
                .getSystemService(CallActivity.AUDIO_SERVICE);
        assertNotNull(audioManager);
        int originalMode = audioManager.getMode();

        ActivityScenario<CallActivity> scenario = ActivityScenario.launch(incomingCallIntent());
        scenario.onActivity(activity -> activity.<View>findViewById(R.id.fabEndCall).performClick());
        SystemClock.sleep(2500);

        assertEquals(originalMode, audioManager.getMode());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assertTrue(!audioManager.isSpeakerphoneOn());
        }
        scenario.close();
    }

    @Test
    public void hangup_cancels_pending_call_activation() {
        AudioManager audioManager = (AudioManager) InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext()
                .getSystemService(CallActivity.AUDIO_SERVICE);
        assertNotNull(audioManager);
        int originalMode = audioManager.getMode();

        ActivityScenario<CallActivity> scenario = ActivityScenario.launch(incomingCallIntent());
        scenario.onActivity(activity -> {
            try {
                Method method = CallActivity.class.getDeclaredMethod(
                        "startTransportAndTimer", TextView.class, Chronometer.class);
                method.setAccessible(true);
                method.invoke(
                        activity,
                        activity.<TextView>findViewById(R.id.connectionState),
                        activity.<Chronometer>findViewById(R.id.callTimer));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to schedule call activation", e);
            }
            activity.<View>findViewById(R.id.fabEndCall).performClick();
        });
        SystemClock.sleep(2500);

        assertEquals(originalMode, audioManager.getMode());
        scenario.close();
    }
}

package com.securecall.app;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class FullScreenIntentSettingsTest {
    @Test public void opensCorrectActionWithoutFallback() {
        check(true, true, null, null, true, 1, 1);
    }

    @Test public void missingPrimaryOpensAppDetails() {
        check(false, true, null, null, true, 2, 1);
    }

    @Test public void handlerDisappearingAfterResolutionFallsBack() {
        check(true, true, new ActivityNotFoundException(), null, true, 2, 2);
    }

    @Test public void deniedPrimaryFallsBack() {
        check(true, true, new SecurityException(), null, true, 2, 2);
    }

    @Test public void bothHandlersMissingContinuesWithoutLaunching() {
        check(false, false, null, null, false, 2, 0);
    }

    @Test public void fallbackDisappearingDoesNotThrow() {
        check(false, true, null, new ActivityNotFoundException(), false, 2, 1);
    }

    @Test public void fallbackDeniedDoesNotThrow() {
        check(false, true, null, new SecurityException(), false, 2, 1);
    }

    private void check(boolean primaryExists, boolean fallbackExists,
            RuntimeException primaryFailure, RuntimeException fallbackFailure,
            boolean expected, int constructedCount, int launchCount) {
        Context activity = mock(Context.class);
        PackageManager pm = mock(PackageManager.class);
        ComponentName handler = mock(ComponentName.class);
        Uri packageUri = mock(Uri.class);
        when(activity.getPackageName()).thenReturn("com.securecall.app.free");
        when(activity.getPackageManager()).thenReturn(pm);
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class, (intent, creation) -> {
                 boolean primary = creation.getCount() == 1;
                 // Literal assertion intentionally detects the original plural-action typo.
                 assertEquals(primary ? "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"
                         : "android.settings.APPLICATION_DETAILS_SETTINGS", creation.arguments().get(0));
                 assertSame(packageUri, creation.arguments().get(1));
                 when(intent.resolveActivity(pm)).thenReturn(
                         (primary ? primaryExists : fallbackExists) ? handler : null);
                 RuntimeException failure = primary ? primaryFailure : fallbackFailure;
                 if (failure != null) doThrow(failure).when(activity).startActivity(intent);
             })) {
            uris.when(() -> Uri.parse("package:com.securecall.app.free")).thenReturn(packageUri);
            assertEquals(expected, FullScreenIntentSettings.open(activity));
            uris.verify(() -> Uri.parse("package:com.securecall.app.free"));
            assertEquals(constructedCount, intents.constructed().size());
            verify(activity, times(launchCount)).startActivity(any(Intent.class));
            for (int i = 0; i < intents.constructed().size(); i++) {
                Intent intent = intents.constructed().get(i);
                verify(intent).resolveActivity(pm);
                if (!(i == 0 ? primaryExists : fallbackExists)) {
                    verify(activity, never()).startActivity(intent);
                }
            }
        }
    }
}

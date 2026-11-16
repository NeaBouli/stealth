package com.securecall.app.update;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import com.securecall.app.BuildConfig;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class UpdateManagerTest {
    private static final String DOWNLOAD_URL = "https://stealthx.tech/download.html";
    private static final String PLAY_ID = "com.securecall.app.free";

    @Test public void manualLabelDoesNotAdvertiseAdbOnly() {
        assertEquals("Check for Updates", UpdateManager.getUpdateLabel());
    }

    @Test public void paidDirectManualCheckOpensOnlyOfficialDownloadPage() {
        assumePaidDirect();
        checkPaidHandoff(null, false);
    }

    @Test public void paidDirectMissingBrowserDoesNotCrashOrTryAnotherDestination() {
        assumePaidDirect();
        checkPaidHandoff(new ActivityNotFoundException(), false);
    }

    @Test public void paidDirectDeniedBrowserDoesNotCrashOrTryAnotherDestination() {
        assumePaidDirect();
        checkPaidHandoff(new SecurityException("synthetic denial"), false);
    }

    @Test public void returningOrCancellingPaidHandoffDoesNotTouchStoredProofOrUpdateState() {
        assumePaidDirect();
        // A browser handoff has no result callback. The next automatic check must remain inert.
        checkPaidHandoff(null, true);
    }

    @Test public void paidDirectAutomaticCheckStillDoesNothing() {
        assumePaidDirect();
        Activity activity = mock(Activity.class);
        UpdateManager.maybeAutoCheck(activity);
        verifyNoInteractions(activity);
    }

    @Test public void freePlayManualCheckStillOpensPlayStore() {
        assumeFree();
        Activity activity = installedBy("com.android.vending");
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedConstruction<Intent> intents = intentsFor("market://details?id=" + PLAY_ID, uris)) {
            UpdateManager.checkAndPromptUpdate(activity);
            assertEquals(1, intents.constructed().size());
            Intent intent = intents.constructed().get(0);
            verify(intent).setPackage("com.android.vending");
            verify(activity).startActivity(intent);
            uris.verify(() -> Uri.parse(DOWNLOAD_URL), never());
        }
    }

    @Test public void freePlayMissingMarketStillUsesExistingPlayWebFallback() {
        assumeFree();
        Activity activity = installedBy("com.android.vending");
        doThrow(new ActivityNotFoundException()).doNothing().when(activity).startActivity(any(Intent.class));
        Uri uri = mock(Uri.class);
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class, (intent, creation) -> {
                 assertEquals(Intent.ACTION_VIEW, creation.arguments().get(0));
                 assertSame(uri, creation.arguments().get(1));
                 when(intent.addFlags(anyInt())).thenReturn(intent);
             })) {
            uris.when(() -> Uri.parse("market://details?id=" + PLAY_ID)).thenReturn(uri);
            uris.when(() -> Uri.parse("https://play.google.com/store/apps/details?id=" + PLAY_ID)).thenReturn(uri);
            UpdateManager.checkAndPromptUpdate(activity);
            assertEquals(2, intents.constructed().size());
            for (Intent intent : intents.constructed()) verify(activity).startActivity(intent);
            uris.verify(() -> Uri.parse(DOWNLOAD_URL), never());
        }
    }

    @Test public void freeDirectAndOtherStoreManualChecksStillStartExistingInAppCheck() {
        assumeFree();
        for (String installer : new String[] {null, "com.android.shell", "adb", "other.store"}) {
            Activity activity = installedBy(installer);
            Toast toast = mock(Toast.class);
            // Do not execute the existing network thread in a routing-only JVM test.
            try (MockedStatic<Toast> toasts = mockStatic(Toast.class);
                 MockedConstruction<Thread> threads = mockConstruction(Thread.class, (thread, creation) -> {
                     assertEquals("update-check", creation.arguments().get(1));
                 })) {
                toasts.when(() -> Toast.makeText(activity, "Checking for updates\u2026", Toast.LENGTH_SHORT))
                        .thenReturn(toast);
                UpdateManager.checkAndPromptUpdate(activity);
                assertEquals(1, threads.constructed().size());
                verify(threads.constructed().get(0)).start();
                verify(toast).show();
                verify(activity, never()).startActivity(any(Intent.class));
            }
        }
    }

    @Test public void freePlayAutomaticCheckRemainsStoreManaged() {
        assumeFree();
        Activity activity = installedBy("com.android.vending");
        UpdateManager.maybeAutoCheck(activity);
        verify(activity, never()).startActivity(any(Intent.class));
        verify(activity, never()).getSharedPreferences(anyString(), anyInt());
    }

    private void checkPaidHandoff(RuntimeException failure, boolean returnToApp) {
        Activity activity = mock(Activity.class);
        Toast toast = mock(Toast.class);
        if (failure != null) doThrow(failure).when(activity).startActivity(any(Intent.class));
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedStatic<Toast> toasts = mockStatic(Toast.class);
             MockedConstruction<Intent> intents = intentsFor(DOWNLOAD_URL, uris)) {
            toasts.when(() -> Toast.makeText(eq(activity), any(CharSequence.class), anyInt())).thenReturn(toast);
            UpdateManager.checkAndPromptUpdate(activity);
            if (returnToApp) UpdateManager.maybeAutoCheck(activity);
            assertEquals(1, intents.constructed().size());
            verify(activity).startActivity(intents.constructed().get(0));
            // No preference/proof access, activity-for-result, installer lookup or fallback intent.
            verifyNoMoreInteractions(activity);
            uris.verify(() -> Uri.parse(DOWNLOAD_URL));
            uris.verifyNoMoreInteractions();
            if (failure == null) {
                toasts.verifyNoInteractions();
            } else {
                toasts.verify(() -> Toast.makeText(activity, "No browser available", Toast.LENGTH_SHORT));
                verify(toast).show();
            }
        }
    }

    private MockedConstruction<Intent> intentsFor(String url, MockedStatic<Uri> uris) {
        Uri uri = mock(Uri.class);
        uris.when(() -> Uri.parse(url)).thenReturn(uri);
        return mockConstruction(Intent.class, (intent, creation) -> {
            assertEquals(Intent.ACTION_VIEW, creation.arguments().get(0));
            assertSame(uri, creation.arguments().get(1));
            when(intent.addFlags(anyInt())).thenReturn(intent);
        });
    }

    @SuppressWarnings("deprecation")
    private Activity installedBy(String installer) {
        Activity activity = mock(Activity.class);
        PackageManager pm = mock(PackageManager.class);
        when(activity.getPackageName()).thenReturn(PLAY_ID);
        when(activity.getPackageManager()).thenReturn(pm);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                InstallSourceInfo source = mock(InstallSourceInfo.class);
                when(pm.getInstallSourceInfo(PLAY_ID)).thenReturn(source);
                when(source.getInstallingPackageName()).thenReturn(installer);
            } else {
                when(pm.getInstallerPackageName(PLAY_ID)).thenReturn(installer);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError(e);
        }
        return activity;
    }

    private void assumePaidDirect() {
        assumeTrue(BuildConfig.FLAVOR.equals("pro") || BuildConfig.FLAVOR.equals("premium"));
    }

    private void assumeFree() {
        assumeTrue(BuildConfig.FLAVOR.equals("free"));
        assertFalse(BuildConfig.BILLING_ENABLED);
        assertFalse(BuildConfig.ACTIVATION_CODE_ENABLED);
        assertFalse(BuildConfig.EXTERNAL_PURCHASE_LINKS_ENABLED);
    }
}

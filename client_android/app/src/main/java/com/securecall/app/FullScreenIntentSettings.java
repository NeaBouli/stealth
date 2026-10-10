package com.securecall.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

/** Optional settings navigation must never prevent app startup. */
final class FullScreenIntentSettings {
    private FullScreenIntentSettings() {}

    // Called only on API 34+ when canUseFullScreenIntent() is false.
    static boolean open(Context activity) {
        Uri packageUri = Uri.parse("package:" + activity.getPackageName());
        if (tryOpen(activity, new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                packageUri))) {
            return true;
        }
        // App details can expose the OEM's permission controls; this does not grant permission.
        return tryOpen(activity, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                packageUri));
    }

    private static boolean tryOpen(Context activity, Intent intent) {
        try {
            if (intent.resolveActivity(activity.getPackageManager()) == null) {
                return false;
            }
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            // Resolution is not a guarantee: the handler may disappear or deny access.
            Log.w("FullScreenIntentSettings", "Settings unavailable; continuing startup", e);
            return false;
        }
    }
}

package com.securecall.app.debug;

import android.util.Log;

/**
 * Minimal debug event bus stub.
 */
public class GhostDebugEventBus {

    private static final String TAG = "GHOST_DEBUG";

    public static void post(String tag, String msg) {
        Log.d(tag != null ? tag : TAG, msg != null ? msg : "");
    }

    public static void postSessionKeysPreview(String tagPrefix, byte[] rx, byte[] tx, byte[] salt) {
        StringBuilder sb = new StringBuilder();
        sb.append("rx=").append(rx != null ? rx.length : 0)
          .append(" tx=").append(tx != null ? tx.length : 0)
          .append(" salt=").append(salt != null ? salt.length : 0);
        post(tagPrefix != null ? tagPrefix : "SESS_KEYS", sb.toString());
    }
}

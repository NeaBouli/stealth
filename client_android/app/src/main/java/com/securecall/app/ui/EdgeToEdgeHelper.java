package com.securecall.app.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Central Android 15 edge-to-edge compatibility for View-based SecureCall screens.
 */
public final class EdgeToEdgeHelper {
    private EdgeToEdgeHelper() {}

    public static void enable(@NonNull ComponentActivity activity) {
        EdgeToEdge.enable(activity);
    }

    public static void applySystemBarPaddingToContent(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            applySystemBarPadding(((ViewGroup) content).getChildAt(0));
        } else if (content != null) {
            applySystemBarPadding(content);
        }
    }

    public static void applySystemBarPadding(@NonNull View view) {
        applySystemBarPadding(view, true, true, true, true);
    }

    public static void applyTopSystemBarPadding(@NonNull View view) {
        applySystemBarPadding(view, false, true, false, false);
    }

    public static void applyBottomSystemBarPadding(@NonNull View view) {
        applySystemBarPadding(view, true, false, true, true);
    }

    public static void applySystemBarPadding(
            @NonNull View view,
            boolean applyLeft,
            boolean applyTop,
            boolean applyRight,
            boolean applyBottom
    ) {
        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(
                    initialLeft + (applyLeft ? bars.left : 0),
                    initialTop + (applyTop ? bars.top : 0),
                    initialRight + (applyRight ? bars.right : 0),
                    initialBottom + (applyBottom ? bars.bottom : 0)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}

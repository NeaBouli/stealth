package com.securecall.app.sc224;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// Platform-only: this separate test-APK process must not depend on the target classloader.
public final class Sc224ReceiverActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 64, 24, 24);
        content.setBackgroundColor(Color.WHITE);
        TextView label = new TextView(this);
        label.setText("SYNTHETIC SC224 HANDOFF\nLocal receiver only. No website loaded.");
        label.setTextColor(Color.BLACK);
        label.setTextSize(20);
        content.addView(label);
        Button cancel = new Button(this);
        cancel.setText("Cancel synthetic handoff");
        cancel.setOnClickListener(view -> finish());
        content.addView(cancel);
        setContentView(content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String target = getPackageName();
        if (!target.endsWith(".devtest.test")) throw new IllegalStateException("Test APK only");
        sendBroadcast(new Intent("com.securecall.sc224.SYNTHETIC_RECEIVER")
                .setPackage(target.substring(0, target.length() - 5))
                .putExtra("launched", getIntent()).putExtra("receiverPackage", target));
    }
}

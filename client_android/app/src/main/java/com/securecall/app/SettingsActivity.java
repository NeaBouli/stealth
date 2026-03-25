package com.securecall.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import com.securecall.app.ui.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FLAG_SECURE: prevent screenshots based on tier and user preference
        applyFlagSecure();

        setContentView(R.layout.fragment_settings);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settingsContainer, new SettingsFragment())
                    .commit();
        }
    }

    private void applyFlagSecure() {
        try {
            String tier = com.securecall.app.config.TierManager.INSTANCE.getCurrentTier(this);
            if ("FREE".equals(tier)) { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); return; }
            if ("PREMIUM".equals(tier)) { getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE); return; }
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs.getBoolean("pref_block_screenshots", true)) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Exception e) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}

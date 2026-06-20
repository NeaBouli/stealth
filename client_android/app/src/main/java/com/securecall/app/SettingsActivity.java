package com.securecall.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.securecall.app.ui.EdgeToEdgeHelper;
import com.securecall.app.ui.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);

        // FLAG_SECURE: prevent screenshots based on tier and user preference
        applyFlagSecure();

        setContentView(R.layout.fragment_settings);
        EdgeToEdgeHelper.applySystemBarPaddingToContent(this);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settingsContainer, new SettingsFragment())
                    .commit();
        }
    }

    private void applyFlagSecure() {
        com.securecall.app.security.WindowSecurityHelper.applyFlagSecure(this);
    }
}

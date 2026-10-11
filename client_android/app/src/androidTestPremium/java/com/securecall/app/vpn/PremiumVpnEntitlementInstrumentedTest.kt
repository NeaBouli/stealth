package com.securecall.app.vpn

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.app.SettingsActivity
import com.securecall.app.config.TierManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumVpnEntitlementInstrumentedTest {
    @Test
    fun unlicensedServiceStartClearsPersistedRestartFlag() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("FREE", TierManager.getCurrentTier(context))
        val prefs = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
        val disabled = CountDownLatch(1)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "premium_vpn_enabled" && !VpnConfigStore.isEnabled(context)) disabled.countDown()
        }
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            prefs.registerOnSharedPreferenceChangeListener(listener)
            try {
                assertTrue(prefs.edit().putBoolean("premium_vpn_enabled", true).commit())
                scenario.onActivity {
                    it.startService(Intent(it, PremiumVpnService::class.java)
                        .setAction(PremiumVpnService.ACTION_START))
                }
                assertTrue("Unlicensed service must disable restart", disabled.await(10, TimeUnit.SECONDS))
                assertFalse(VpnConfigStore.isEnabled(context))
                assertEquals(PremiumVpnState.Status.OFF, PremiumVpnState.status)
            } finally {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
                context.stopService(Intent(context, PremiumVpnService::class.java))
                VpnConfigStore.setEnabled(context, false)
            }
        }
    }
}

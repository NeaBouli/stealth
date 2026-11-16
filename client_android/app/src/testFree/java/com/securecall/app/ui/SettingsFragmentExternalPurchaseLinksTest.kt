package com.securecall.app.ui

import androidx.preference.Preference
import android.content.Intent
import com.securecall.app.BuildConfig
import com.securecall.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SettingsFragmentExternalPurchaseLinksTest {
    @Test
    fun freeBuildDisablesExternalPurchaseLinks() {
        assertFalse(BuildConfig.EXTERNAL_PURCHASE_LINKS_ENABLED)
    }

    @Test
    fun freeSettingsSeparateInternalUpgradeFromExternalPurchasesForEveryTier() {
        for (tier in listOf("FREE", "PRO", "PREMIUM")) {
            val settings = Mockito.mock(SettingsFragment::class.java, Answers.CALLS_REAL_METHODS)
            val upgrade: Preference = mock()
            val customIdBuy: Preference = mock()
            doReturn(upgrade).whenever(settings).findPreference<Preference>("pref_upgrade")
            doReturn(customIdBuy).whenever(settings).findPreference<Preference>("pref_custom_id_buy")
            doReturn("Upgrade to Pro / Premium").whenever(settings).getString(R.string.pref_play_upgrade)
            doReturn("Upgrade to Premium").whenever(settings).getString(R.string.pref_play_upgrade_premium)
            doReturn("Purchases are currently unavailable").whenever(settings).getString(R.string.upgrade_purchases_unavailable)

            settings.configureExternalPurchaseLinks(tier)

            verify(customIdBuy).isVisible = false
            verify(customIdBuy).setOnPreferenceClickListener(null)
            verify(customIdBuy, never()).setOnPreferenceClickListener(any())
            verify(upgrade).isVisible = (tier != "PREMIUM")
            verify(upgrade).title = if (tier == "PRO") "Upgrade to Premium" else "Upgrade to Pro / Premium"
            verify(upgrade).summary = "Purchases are currently unavailable"
            verify(upgrade).setOnPreferenceClickListener(null)
            if (tier == "PREMIUM") {
                verify(upgrade, never()).setOnPreferenceClickListener(any())
            } else {
                val listener = argumentCaptor<Preference.OnPreferenceClickListener>()
                verify(upgrade).setOnPreferenceClickListener(listener.capture())
                doNothing().whenever(settings).startActivity(any())
                Mockito.mockConstruction(Intent::class.java).use { construction ->
                    assertTrue(listener.firstValue.onPreferenceClick(upgrade))
                    val intent = construction.constructed().single()
                    verify(intent).setClassName(eq(BuildConfig.APPLICATION_ID), eq("com.securecall.app.billing.UpgradeActivity"))
                    verify(settings).startActivity(intent)
                }
            }
        }
    }
}

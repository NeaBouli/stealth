package com.securecall.app.ui

import androidx.preference.Preference
import com.securecall.app.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.kotlin.any
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
    fun freeSettingsHidePurchasePromptsAndClearListenersForEveryTier() {
        for (tier in listOf("FREE", "PRO", "PREMIUM")) {
            val settings = Mockito.mock(SettingsFragment::class.java, Answers.CALLS_REAL_METHODS)
            val upgrade: Preference = mock()
            val customIdBuy: Preference = mock()
            doReturn(upgrade).whenever(settings).findPreference<Preference>("pref_upgrade")
            doReturn(customIdBuy).whenever(settings).findPreference<Preference>("pref_custom_id_buy")

            settings.configureExternalPurchaseLinks(tier)

            for (preference in listOf(upgrade, customIdBuy)) {
                verify(preference).isVisible = false
                verify(preference).setOnPreferenceClickListener(null)
                verify(preference, never()).setOnPreferenceClickListener(any())
            }
        }
    }
}

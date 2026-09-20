package com.securecall.app.vpn

import android.content.Context
import android.content.SharedPreferences
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PremiumVpnControllerTest {
    @Test
    fun `unlicensed premium package cannot start foreground VPN service`() {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)

        PremiumVpnController.start(context)

        verify(context, never()).startForegroundService(any())
        verify(context, never()).startService(any())
        verify(prefs, never()).edit()
    }
}

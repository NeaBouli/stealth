package com.securecall.app.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

internal object PremiumVpnController {
    fun permissionIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        if (!VpnConfigStore.hasConfig(context)) return
        VpnConfigStore.setEnabled(context, true)
        ContextCompat.startForegroundService(
            context,
            Intent(context, PremiumVpnService::class.java).setAction(PremiumVpnService.ACTION_START)
        )
    }

    fun stop(context: Context) {
        VpnConfigStore.setEnabled(context, false)
        context.startService(
            Intent(context, PremiumVpnService::class.java).setAction(PremiumVpnService.ACTION_STOP)
        )
    }

    fun permissionGranted(resultCode: Int): Boolean = resultCode == Activity.RESULT_OK
}

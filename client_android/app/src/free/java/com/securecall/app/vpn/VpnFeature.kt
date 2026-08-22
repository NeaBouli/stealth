package com.securecall.app.vpn

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.securecall.app.ui.SettingsFragment

object VpnFeature {
    fun configure(
        fragment: SettingsFragment,
        permissionLauncher: ActivityResultLauncher<Intent>
    ) = Unit

    fun refresh(fragment: SettingsFragment) = Unit

    fun onPermissionResult(fragment: SettingsFragment, resultCode: Int) = Unit
}

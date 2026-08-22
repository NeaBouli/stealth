package com.securecall.app.vpn

internal object PremiumVpnState {
    enum class Status { OFF, CONNECTING, ACTIVE, ERROR }

    @Volatile
    var status: Status = Status.OFF
        internal set
}

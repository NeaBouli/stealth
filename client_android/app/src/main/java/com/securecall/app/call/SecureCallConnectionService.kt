package com.securecall.app.call

import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
class SecureCallConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection = IncomingCallTelecom.createConnection(sessionId(request))

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ) = IncomingCallTelecom.rejectIncomingCall(sessionId(request))

    private fun sessionId(request: ConnectionRequest): String =
        request.extras?.getString(IncomingCallTelecom.EXTRA_SESSION_ID).orEmpty()
}

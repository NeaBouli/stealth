package com.securecall.app.call

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.securecall.app.IncomingCallActivity
import com.securecall.app.R
import com.securecall.app.net.WebSocketService
import com.securecall.app.notifications.IncomingCallNotifications

/** Telecom owns admission and UI timing; signaling and media remain in WebSocketService. */
object IncomingCallTelecom {
    private const val TAG = "IncomingCallTelecom"
    internal const val EXTRA_SESSION_ID = "com.securecall.app.telecom.SESSION_ID"
    const val EXTRA_ANSWER = "com.securecall.app.telecom.ANSWER"
    const val EXTRA_MANAGED = "com.securecall.app.telecom.MANAGED"
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val calls = mutableMapOf<String, Call>()
    private val endedCalls = EndedIncomingCalls()

    private class Call(
        val context: Context,
        val intent: Intent,
        val callerName: String,
        val showUi: () -> Unit,
        val lifecycle: IncomingCallLifecycle = IncomingCallLifecycle(),
        var connection: Connection? = null,
        var timeout: Runnable? = null,
    )

    /** Only call after authenticating the invite. False means use the existing UI fallback. */
    @Synchronized
    fun reportIncomingCall(
        context: Context,
        sessionId: String,
        callerName: String,
        incomingIntent: Intent,
        showUi: () -> Unit,
    ): Boolean {
        if (endedCalls.contains(sessionId)) {
            // A delayed transport delivery may have re-adopted this invite before UI routing.
            WebSocketService.instance?.takeIf { it.getCurrentSessionId() == sessionId }?.clearSession()
            return true
        }
        if (IncomingCallActivity.isAcceptedSession(sessionId) || calls.containsKey(sessionId)) return true
        // FEATURE_TELECOM only exists from Android 13; older devices declare the
        // connection-service feature instead (Galaxy A21s/Tab S4 QA, 2026-10-16).
        val telecomFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            PackageManager.FEATURE_TELECOM else PackageManager.FEATURE_CONNECTION_SERVICE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !context.packageManager.hasSystemFeature(telecomFeature)) return false
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        val app = context.applicationContext
        val call = Call(app, Intent(incomingIntent).putExtra(EXTRA_MANAGED, true), callerName, showUi)
        try {
            val account = PhoneAccountHandle(ComponentName(app, SecureCallConnectionService::class.java), "incoming")
            telecom.registerPhoneAccount(
                PhoneAccount.builder(account, app.getString(R.string.app_name))
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_SIP))
                    .build()
            )
            calls[sessionId] = call
            incomingIntent.putExtra(EXTRA_MANAGED, true)
            // The activity's 60 s deadline cannot run if the screen never opens.
            // Keep the same ring limit while Telecom is binding or showing its conflict UI.
            call.timeout = Runnable {
                synchronized(this) {
                    if (calls[sessionId] === call && call.lifecycle.shouldTimeout()) {
                        terminate(sessionId, DisconnectCause.MISSED, "no_answer")
                    }
                }
            }.also { handler.postDelayed(it, RingTimeoutPolicy.OUTGOING_RING_TIMEOUT_MS) }
            val address = Uri.fromParts(PhoneAccount.SCHEME_SIP, incomingIntent.getStringExtra("callerClientId"), null)
            telecom.addNewIncomingCall(account, Bundle().apply {
                putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address)
                putString(EXTRA_SESSION_ID, sessionId)
            })
            Log.d(TAG, "Incoming call reported to Telecom")
            return true
        } catch (e: RuntimeException) {
            // No Telecom admission callback is possible when registration/submission fails.
            release(sessionId, DisconnectCause.ERROR)
            incomingIntent.removeExtra(EXTRA_MANAGED)
            Log.w(TAG, "Telecom unavailable; using existing incoming call UI", e)
            return false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Synchronized
    internal fun createConnection(sessionId: String): Connection {
        val call = calls[sessionId]
        if (call == null || !call.lifecycle.attach()) {
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED))
        }
        return object : Connection() {
            override fun onShowIncomingCallUi() {
                synchronized(this@IncomingCallTelecom) {
                    if (calls[sessionId] === call && call.lifecycle.showUi()) call.showUi()
                }
            }

            override fun onAnswer() = answerFromTelecom(sessionId, call)
            override fun onAnswer(videoState: Int) = onAnswer()
            override fun onReject() = terminateIfCurrent(sessionId, call, DisconnectCause.REJECTED)
            override fun onReject(rejectReason: Int) = onReject()
            override fun onReject(replyMessage: String?) = onReject()
            override fun onDisconnect() = terminateIfCurrent(sessionId, call, DisconnectCause.LOCAL)
            override fun onAbort() = onDisconnect()
            override fun onSilence() {
                synchronized(this@IncomingCallTelecom) {
                    if (calls[sessionId] === call) {
                        WebSocketService.instance?.takeIf { it.getCurrentSessionId() == sessionId }
                            ?.stopIncomingRingtone()
                        IncomingCallNotifications.silence(call.context)
                    }
                }
            }
        }.apply {
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
            audioModeIsVoip = true
            setAddress(
                Uri.fromParts(PhoneAccount.SCHEME_SIP, call.intent.getStringExtra("callerClientId"), null),
                TelecomManager.PRESENTATION_ALLOWED,
            )
            setCallerDisplayName(call.callerName, TelecomManager.PRESENTATION_ALLOWED)
            call.connection = this
            setRinging()
        }
    }

    /** A policy denial (including emergency calls) must never fall back to ringing UI. */
    @Synchronized
    internal fun rejectIncomingCall(sessionId: String) {
        if (calls.containsKey(sessionId)) terminate(sessionId, DisconnectCause.REJECTED, "busy")
    }

    @Synchronized
    fun accepted(sessionId: String) {
        val call = calls[sessionId] ?: return
        if (call.lifecycle.answer()) {
            call.timeout?.let(handler::removeCallbacks)
            IncomingCallNotifications.cancelAll(call.context)
            call.connection?.setActive()
        }
    }

    @Synchronized
    private fun answerFromTelecom(sessionId: String, call: Call) {
        if (calls[sessionId] !== call || !call.lifecycle.shouldTimeout()) return
        accepted(sessionId)
        try {
            // Enter the same accept path used by the in-app answer button.
            call.context.startActivity(Intent(call.intent).putExtra(EXTRA_ANSWER, true))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to open call UI for Telecom answer", e)
            terminate(sessionId, DisconnectCause.ERROR, "user_hangup")
        }
    }

    @Synchronized
    fun isAnswered(sessionId: String): Boolean = calls[sessionId]?.lifecycle?.isActive() == true

    @Synchronized
    fun hasCall(sessionId: String): Boolean = calls.containsKey(sessionId)

    @Synchronized
    private fun terminateIfCurrent(sessionId: String, call: Call, cause: Int) {
        if (calls[sessionId] === call) terminate(sessionId, cause, "user_hangup")
    }

    private fun terminate(sessionId: String, cause: Int, reason: String) {
        val context = calls[sessionId]?.context ?: return
        end(sessionId, cause)
        if (WebSocketService.instance?.endCallFromTelecom(sessionId, reason) != true) {
            IncomingCallActivity.dismissIfActive(sessionId)
            IncomingCallNotifications.cancelAll(context)
        }
    }

    /** Remove pending requests too, so a late framework callback cannot revive an ended call. */
    @Synchronized
    fun end(sessionId: String, cause: Int = DisconnectCause.LOCAL) {
        endedCalls.remember(sessionId)
        release(sessionId, cause)
    }

    private fun release(sessionId: String, cause: Int) {
        val call = calls.remove(sessionId) ?: return
        call.lifecycle.end()
        call.timeout?.let(handler::removeCallbacks)
        IncomingCallNotifications.cancelAll(call.context)
        call.connection?.apply {
            setDisconnected(DisconnectCause(cause))
            destroy()
        }
    }
}

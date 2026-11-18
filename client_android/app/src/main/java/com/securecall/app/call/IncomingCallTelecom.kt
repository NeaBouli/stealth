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

    /**
     * Only call after authenticating the invite. False means use the existing UI fallback.
     * Lock order: the Telecom monitor is never held while calling the service or UI.
     */
    fun reportIncomingCall(
        context: Context,
        sessionId: String,
        callerName: String,
        incomingIntent: Intent,
        showUi: () -> Unit,
    ): Boolean {
        if (synchronized(this) { endedCalls.contains(sessionId) }) {
            // A delayed transport delivery may have re-adopted this invite before UI routing.
            WebSocketService.instance?.clearSessionIfOwner(sessionId)
            return true
        }
        return admitIncomingCall(context, sessionId, callerName, incomingIntent, showUi)
    }

    @Synchronized
    private fun admitIncomingCall(
        context: Context,
        sessionId: String,
        callerName: String,
        incomingIntent: Intent,
        showUi: () -> Unit,
    ): Boolean {
        if (endedCalls.contains(sessionId)) return true
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
                val expired = synchronized(this) { calls[sessionId] === call && call.lifecycle.shouldTimeout() }
                if (expired) terminate(sessionId, call, DisconnectCause.MISSED, "no_answer")
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
                val show = synchronized(this@IncomingCallTelecom) {
                    calls[sessionId] === call && call.lifecycle.showUi()
                }
                if (show) call.showUi()
            }

            override fun onAnswer() = answerFromTelecom(sessionId, call)
            override fun onAnswer(videoState: Int) = onAnswer()
            override fun onReject() = terminate(sessionId, call, DisconnectCause.REJECTED, "user_hangup")
            override fun onReject(rejectReason: Int) = onReject()
            override fun onReject(replyMessage: String?) = onReject()
            override fun onDisconnect() = terminate(sessionId, call, DisconnectCause.LOCAL, "user_hangup")
            override fun onAbort() = onDisconnect()
            override fun onSilence() {
                if (synchronized(this@IncomingCallTelecom) { calls[sessionId] !== call }) return
                WebSocketService.instance?.stopIncomingRingtoneIfOwner(sessionId)
                IncomingCallNotifications.silence(call.context, sessionId)
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
    internal fun rejectIncomingCall(sessionId: String) {
        val call = synchronized(this) { calls[sessionId] } ?: return
        terminate(sessionId, call, DisconnectCause.REJECTED, "busy")
    }

    @Synchronized
    fun accepted(sessionId: String) {
        val call = calls[sessionId] ?: return
        if (call.lifecycle.answer()) {
            call.timeout?.let(handler::removeCallbacks)
            IncomingCallNotifications.cancel(call.context, sessionId)
            call.connection?.setActive()
        }
    }

    private fun answerFromTelecom(sessionId: String, call: Call) {
        val admitted = synchronized(this) {
            (calls[sessionId] === call && call.lifecycle.shouldTimeout()).also { if (it) accepted(sessionId) }
        }
        if (!admitted) return
        try {
            // Enter the same accept path used by the in-app answer button.
            call.context.startActivity(Intent(call.intent).putExtra(EXTRA_ANSWER, true))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to open call UI for Telecom answer", e)
            terminate(sessionId, call, DisconnectCause.ERROR, "user_hangup")
        }
    }

    @Synchronized
    fun isAnswered(sessionId: String): Boolean = calls[sessionId]?.lifecycle?.isActive() == true

    @Synchronized
    fun hasCall(sessionId: String): Boolean = calls.containsKey(sessionId)

    /** Ends only this admitted call; service/UI side effects run after the monitor is released. */
    private fun terminate(sessionId: String, call: Call, cause: Int, reason: String) {
        val owned = synchronized(this) { (calls[sessionId] === call).also { if (it) end(sessionId, cause) } }
        if (!owned) return
        if (WebSocketService.instance?.endCallFromTelecom(sessionId, reason) != true) {
            IncomingCallActivity.dismissIfActive(sessionId)
            IncomingCallNotifications.cancel(call.context, sessionId)
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
        IncomingCallNotifications.cancel(call.context, sessionId)
        call.connection?.apply {
            setDisconnected(DisconnectCause(cause))
            destroy()
        }
    }
}

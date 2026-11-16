package com.securecall.app

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.securecall.app.net.HeartbeatClient
import com.securecall.app.net.WebSocketService
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.io.IOException
import java.util.Timer

/** Real lifecycle callbacks and client transitions; no Application/native startup or real IO. */
class PolicyStopResumeTest {
    private lateinit var loopers: MockedStatic<Looper>
    private lateinit var handlers: MockedConstruction<Handler>
    private lateinit var timers: MockedConstruction<Timer>
    private lateinit var preferences: MockedStatic<PreferenceManager>
    private lateinit var starts: MockedStatic<ContextCompat>
    private lateinit var app: SecureCallApplication
    private lateinit var lifecycle: Application.ActivityLifecycleCallbacks
    private val activity: Activity = mock()
    private val prefs: SharedPreferences = mock()
    private val delayed = mutableListOf<Runnable>()
    private var startCount = 0
    private lateinit var service: WebSocketService
    private lateinit var client: HeartbeatClient
    private val transport: OkHttpClient = mock()
    private val socket: WebSocket = mock()

    @Before
    fun setUp() {
        loopers = Mockito.mockStatic(Looper::class.java)
        loopers.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mock())
        handlers = Mockito.mockConstruction(Handler::class.java) { handler, _ ->
            whenever(handler.postDelayed(any(), any())).thenAnswer { delayed += it.getArgument<Runnable>(0); true }
            doAnswer { delayed.clear(); null }.whenever(handler).removeCallbacksAndMessages(null)
        }
        timers = Mockito.mockConstruction(Timer::class.java)
        preferences = Mockito.mockStatic(PreferenceManager::class.java)
        preferences.`when`<SharedPreferences> { PreferenceManager.getDefaultSharedPreferences(any()) }.thenReturn(prefs)
        whenever(prefs.getBoolean(eq("pref_background_service"), any())).thenReturn(false)
        starts = Mockito.mockStatic(ContextCompat::class.java)
        starts.`when`<android.content.ComponentName> { ContextCompat.startForegroundService(any(), any()) }
            .thenAnswer { startCount++; null }

        app = spy(SecureCallApplication())
        doAnswer { lifecycle = it.getArgument(0); null }.whenever(app).registerActivityLifecycleCallbacks(any())
        doReturn(true).whenever(app).stopService(any())
        app.registerSignalingLifecycleCallbacks()
        service = spy(WebSocketService())
        doReturn(app).whenever(service).application
        WebSocketService.instance = service
        client = HeartbeatClient("wss://lifecycle.test/ws", mock())
        setField(client, "okClient", transport)
        whenever(transport.newWebSocket(any<Request>(), any<WebSocketListener>())).thenReturn(socket)
        client.connect()
        client.onOpen(socket, mock<Response>())
        setField(client, "lastConnectAttempt", System.currentTimeMillis() - 10_001L)
        setField(service, "client", client)
        setField(service, "isConnected", true)
        service.isRegistered = true
    }

    @After
    fun tearDown() {
        WebSocketService.instance = null
        starts.close()
        preferences.close()
        timers.close()
        handlers.close()
        loopers.close()
    }

    @Test
    fun completedPolicyStopResumesExactlyOnceOnWarmReturn() {
        backgroundAndStop()
        service.onDestroy()
        assertEquals(0, startCount)
        lifecycle.onActivityStarted(activity)
        lifecycle.onActivityStarted(mock())
        assertEquals(1, startCount)
    }

    @Test
    fun foregroundBeforeDestroyWaitsForCompletionThenResumes() {
        backgroundAndStop()
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
        service.onDestroy()
        assertEquals(1, startCount)
    }

    @Test
    fun backgroundAgainDuringDestroyDefersResumeUntilNextReturn() {
        backgroundAndStop()
        lifecycle.onActivityStarted(activity)
        lifecycle.onActivityStopped(activity)
        service.onDestroy()
        assertEquals(0, startCount)
        lifecycle.onActivityStarted(activity)
        assertEquals(1, startCount)
    }

    @Test
    fun returnBeforeTimerCancelsStopAndDoesNotReconnectLiveService() {
        lifecycle.onActivityStarted(activity)
        lifecycle.onActivityStopped(activity)
        assertEquals(1, delayed.size)
        val cancelledStop = delayed.single()
        lifecycle.onActivityStarted(activity)
        assertTrue(delayed.isEmpty())
        cancelledStop.run() // Even a dispatched stale timer rechecks foreground state.
        verify(app, never()).stopService(any())
        assertEquals(0, startCount)
        verify(transport, times(1)).newWebSocket(any<Request>(), any<WebSocketListener>())
    }

    @Test
    fun anotherStartedActivityPreventsPolicyStop() {
        lifecycle.onActivityStarted(activity)
        lifecycle.onActivityStarted(mock())
        lifecycle.onActivityStopped(activity)
        assertTrue(delayed.isEmpty())
        app.stopIdleSignaling(35)
        verify(app, never()).stopService(any())
    }

    @Test
    fun api31DisabledPreferenceStopsAndForegroundStillResumes() {
        app.stopIdleSignaling(31)
        verify(app).stopService(any())
        service.onDestroy()
        lifecycle.onActivityStarted(activity)
        assertEquals(1, startCount)
    }

    @Test
    fun api35EnabledLegacyPreferenceStillStopsAndResumes() {
        whenever(prefs.getBoolean(eq("pref_background_service"), any())).thenReturn(true)
        app.stopIdleSignaling(35)
        verify(app).stopService(any())
        service.onDestroy()
        lifecycle.onActivityStarted(activity)
        assertEquals(1, startCount)
    }

    @Test
    fun api31EnabledPreferenceKeepsLiveService() {
        whenever(prefs.getBoolean(eq("pref_background_service"), any())).thenReturn(true)
        app.stopIdleSignaling(31)
        verify(app, never()).stopService(any())
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
    }

    @Test
    fun activeCallPreventsPolicyStop() {
        setField(service, "_currentSessionId", "active-test-call")
        app.stopIdleSignaling(35)
        verify(app, never()).stopService(any())
    }

    @Test
    fun ordinaryDestructionOrMissingServiceDoesNotGrantResume() {
        service.onDestroy()
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
    }

    @Test
    fun unsuccessfulStopRequestDoesNotGrantResume() {
        doReturn(false).whenever(app).stopService(any())
        app.stopIdleSignaling(31)
        service.onDestroy()
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
    }

    @Test
    fun throwingStopRequestDoesNotGrantResume() {
        doThrow(IllegalStateException("test stop rejection")).whenever(app).stopService(any())
        app.stopIdleSignaling(31)
        service.onDestroy()
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
    }

    @Test
    fun replacementAppearingAfterDestructionConsumesResumeWithoutAnotherStart() {
        backgroundAndStop()
        service.onDestroy()
        WebSocketService.instance = mock()
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
    }

    @Test
    fun startFailureKeepsIntentForNextActivityStartWithoutRetryLoop() {
        backgroundAndStop()
        service.onDestroy()
        starts.`when`<android.content.ComponentName> { ContextCompat.startForegroundService(any(), any()) }
            .thenThrow(IllegalStateException("test start rejection"))
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
        starts.reset()
        starts.`when`<android.content.ComponentName> { ContextCompat.startForegroundService(any(), any()) }
            .thenAnswer { startCount++; null }
        lifecycle.onActivityStarted(mock())
        assertEquals(1, startCount)
    }

    @Test
    fun connectedButUnregisteredServiceDoesNotGrantAutomaticResume() {
        service.isRegistered = false
        assertTrue(service.isConnected)
        assertEquals(HeartbeatClient.State.CONNECTED, client.getState())
        backgroundAndStop()
        destroyAndReturnWithoutResume()
    }

    @Test
    fun manualDisconnectBeforeDestructionIsNotRevived() {
        backgroundAndStop()
        service.manualDisconnect()
        destroyAndReturnWithoutResume()
    }

    @Test
    fun auth400xBeforeDestructionIsNotRevived() {
        backgroundAndStop()
        for (code in listOf(4000, 4003, 4099)) {
            client.onClosing(socket, code, "policy rejection")
        }
        destroyAndReturnWithoutResume()
    }

    @Test
    fun clean1000ArrivingAfterStopRequestIsNotRevived() {
        backgroundAndStop()
        client.onClosing(socket, 1000, "superseded")
        destroyAndReturnWithoutResume()
    }

    @Test
    fun recoveryInProgressIsNotReplacedByFreshClient() {
        setField(client, "inRecoveryMode", true)
        backgroundAndStop()
        destroyAndReturnWithoutResume()
    }

    @Test
    fun retryBackoffIsNotReplacedByFreshClient() {
        client.onFailure(socket, IOException("429"), null)
        assertEquals(300_000L, getField(client, "reconnectDelay"))
        backgroundAndStop()
        destroyAndReturnWithoutResume()
        assertEquals(300_000L, getField(client, "reconnectDelay"))
    }

    @Test
    fun dnsBackoffIsNotReplacedByFreshClient() {
        client.onFailure(socket, java.net.UnknownHostException("test host"), null)
        assertEquals(30_000L, getField(client, "reconnectDelay"))
        backgroundAndStop()
        destroyAndReturnWithoutResume()
        assertEquals(30_000L, getField(client, "reconnectDelay"))
    }

    @Test
    fun pendingRetryCannotGrantResumeEvenWithConnectedState() {
        setField(client, "reconnectPending", true)
        backgroundAndStop()
        destroyAndReturnWithoutResume()
    }

    @Test
    fun connectingClientDoesNotGrantResume() {
        setField(client, "state", HeartbeatClient.State.CONNECTING)
        backgroundAndStop()
        destroyAndReturnWithoutResume()
    }

    @Test
    fun recentConnectDoesNotResetFlapHistoryViaLifecycleRestart() {
        setField(client, "lastConnectAttempt", System.currentTimeMillis())
        backgroundAndStop()
        destroyAndReturnWithoutResume()
    }

    @Test
    fun callbacksAndReconnectQueuedForDestroyedServiceCannotReviveIt() {
        service.onDestroy()
        service.onConnected()
        service.onDisconnected()
        service.onError(IOException("late callback"))
        service.forceReconnect()
        assertFalse(service.isConnected)
        assertFalse(service.isRegistered)
        verify(transport, times(1)).newWebSocket(any<Request>(), any<WebSocketListener>())
    }

    @Test
    fun destructionClearsOwnStateAndPublishesOfflineAfterRemovingInstance() {
        var offline = 0
        service.statusCallbackOffline = {
            assertNull(WebSocketService.instance)
            assertFalse(service.isConnected)
            assertFalse(service.isRegistered)
            offline++
        }
        service.onDestroy()
        assertEquals(1, offline)
        assertFalse(service.isConnected)
        assertFalse(service.isRegistered)
    }

    @Test
    fun obsoleteDestructionDoesNotClearOrPublishForReplacement() {
        backgroundAndStop()
        val replacement: WebSocketService = mock()
        WebSocketService.instance = replacement
        var offline = 0
        service.statusCallbackOffline = { offline++ }
        service.onDestroy()
        assertSame(replacement, WebSocketService.instance)
        assertEquals(0, offline)
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
    }

    private fun backgroundAndStop() {
        lifecycle.onActivityStarted(activity)
        lifecycle.onActivityStopped(activity)
        delayed.removeAt(0).run()
        verify(app).stopService(any())
    }

    private fun destroyAndReturnWithoutResume() {
        service.onDestroy()
        lifecycle.onActivityStarted(activity)
        assertEquals(0, startCount)
    }

    private fun setField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun getField(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
}

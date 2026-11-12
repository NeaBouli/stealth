package com.securecall.app.net

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

/**
 * Issue #211: the heartbeat staleness check clears `ws` before cancelling the
 * socket, so the resulting onFailure() is ignored as stale. The staleness path
 * itself must schedule exactly one reconnect, while intentional close, auth
 * hard stops and recovery mode stay suppressed.
 *
 * No real network, timers, sleeps or threads: Handler/Looper and Timer are
 * mocked and their queued callbacks are run by hand.
 */
class HeartbeatClientStaleReconnectTest {

    private lateinit var looper: MockedStatic<Looper>
    private lateinit var handlers: MockedConstruction<Handler>
    private lateinit var timers: MockedConstruction<Timer>

    private val posted = mutableListOf<Runnable>()
    private val delayed = mutableListOf<Runnable>()
    private val timerTasks = mutableListOf<TimerTask>()

    private val sockets = mutableListOf<WebSocket>()
    private val okClient: OkHttpClient = mock()
    private val listener: HeartbeatClient.Listener = mock()
    private lateinit var client: HeartbeatClient

    @Before
    fun setUp() {
        looper = Mockito.mockStatic(Looper::class.java)
        looper.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mock())
        handlers = Mockito.mockConstruction(Handler::class.java) { handler, _ ->
            whenever(handler.post(any())).thenAnswer { posted += it.getArgument<Runnable>(0); true }
            whenever(handler.postDelayed(any(), any())).thenAnswer { delayed += it.getArgument<Runnable>(0); true }
        }
        timers = Mockito.mockConstruction(Timer::class.java) { timer, _ ->
            doAnswer { timerTasks += it.getArgument<TimerTask>(0); null }
                .whenever(timer).scheduleAtFixedRate(any<TimerTask>(), any<Long>(), any<Long>())
        }
        whenever(okClient.newWebSocket(any<Request>(), any<WebSocketListener>())).thenAnswer {
            mock<WebSocket>().also { sockets += it }
        }

        client = HeartbeatClient("wss://heartbeat.test/ws", listener)
        setField("okClient", okClient)
        client.connect()
        client.onOpen(sockets.single(), mock<Response>())
        assertEquals(HeartbeatClient.State.CONNECTED, client.getState())
    }

    @After
    fun tearDown() {
        timers.close()
        handlers.close()
        looper.close()
    }

    @Test
    fun staleHeartbeatSchedulesExactlyOneReconnect() {
        val stale = sockets.single()
        triggerStaleness()
        runPosted()
        verify(stale).cancel()
        // OkHttp reports the cancel asynchronously; ws is already null, so this is ignored.
        client.onFailure(stale, IOException("Canceled"), null)

        assertEquals(1, delayed.size)
        delayed.removeAt(0).run()
        verify(okClient, times(2)).newWebSocket(any<Request>(), any<WebSocketListener>())
        assertEquals(HeartbeatClient.State.CONNECTING, client.getState())
    }

    @Test
    fun intentionalCloseDuringStalenessDoesNotReconnect() {
        triggerStaleness()
        client.close()
        runPosted()

        assertEquals(0, delayed.size)
        verify(okClient, times(1)).newWebSocket(any<Request>(), any<WebSocketListener>())
    }

    @Test
    fun authHardStopDuringStalenessDoesNotReconnect() {
        triggerStaleness()
        client.onClosing(sockets.single(), 4003, "unauthorized")
        runPosted()

        assertEquals(0, delayed.size)
    }

    @Test
    fun recoveryModeSuppressesStalenessReconnect() {
        triggerStaleness()
        setField("inRecoveryMode", true)
        runPosted()

        assertEquals(0, delayed.size)
    }

    private fun triggerStaleness() {
        setField("_lastSeen", 0L)
        assertNotNull(timerTasks.lastOrNull())
        timerTasks.last().run()
        assertEquals(1, posted.size)
    }

    private fun runPosted() {
        while (posted.isNotEmpty()) posted.removeAt(0).run()
    }

    private fun setField(name: String, value: Any?) {
        HeartbeatClient::class.java.getDeclaredField(name).apply { isAccessible = true }.set(client, value)
    }
}

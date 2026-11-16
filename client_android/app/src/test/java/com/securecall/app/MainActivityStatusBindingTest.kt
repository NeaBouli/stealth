package com.securecall.app

import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import com.google.android.material.appbar.MaterialToolbar
import com.securecall.app.net.WebSocketService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

class MainActivityStatusBindingTest {
    private lateinit var loopers: MockedStatic<Looper>
    private lateinit var handlers: MockedConstruction<Handler>
    private lateinit var activity: MainActivity
    private val toolbar: MaterialToolbar = mock()
    private val ui = mutableListOf<Runnable>()
    private val delayed = mutableListOf<Runnable>()

    @Before
    fun setUp() {
        loopers = Mockito.mockStatic(Looper::class.java)
        loopers.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mock())
        handlers = Mockito.mockConstruction(Handler::class.java) { handler, _ ->
            whenever(handler.postDelayed(any(), any())).thenAnswer { delayed += it.getArgument<Runnable>(0); true }
        }
        activity = Mockito.mock(MainActivity::class.java, Answers.CALLS_REAL_METHODS)
        doReturn(mock<Resources>()).whenever(activity).resources
        doAnswer { ui += it.getArgument<Runnable>(0); null }.whenever(activity).runOnUiThread(any())
        setField(activity, "toolbar", toolbar)
    }

    @After
    fun tearDown() {
        WebSocketService.instance = null
        handlers.close()
        loopers.close()
    }

    @Test
    fun rebindDetachesOldInstanceAndRefreshesAlreadyConnectedReplacement() {
        val old = service(connected = false)
        WebSocketService.instance = old
        wire()
        val current = service(connected = true)
        WebSocketService.instance = current
        wire()
        assertNull(old.statusCallbackOnline)
        assertNull(old.statusCallbackOffline)
        assertNotNull(current.statusCallbackOnline)
        assertNotNull(current.statusCallbackOffline)
        verify(toolbar).setSubtitle("\u25CF Connected")
        verify(current, never()).forceReconnect()
    }

    @Test
    fun queuedObsoleteOfflineCannotOverwriteNewConnectedStatus() {
        val old = service(connected = false)
        WebSocketService.instance = old
        wire()
        old.statusCallbackOffline!!.invoke()
        val current = service(connected = true)
        WebSocketService.instance = current
        wire()
        clearInvocations(toolbar)
        ui.forEach(Runnable::run)
        verify(toolbar, never()).setSubtitle("\u25CF Disconnected")
    }

    @Test
    fun destructionOfflineUsesExistingBoundedRetryToBindReplacement() {
        val old = service(connected = true)
        WebSocketService.instance = old
        wire()
        WebSocketService.instance = null
        old.statusCallbackOffline!!.invoke()
        ui.removeAt(0).run()
        assertEquals(1, delayed.size)
        val current = service(connected = true)
        WebSocketService.instance = current
        delayed.removeAt(0).run()
        assertNotNull(current.statusCallbackOnline)
        verify(toolbar, times(2)).setSubtitle("\u25CF Connected")
    }

    @Test
    fun sameLiveInstanceRefreshDoesNotReconnect() {
        val current = service(connected = true)
        WebSocketService.instance = current
        repeat(2) { wire() }
        verify(toolbar, times(2)).setSubtitle("\u25CF Connected")
        verify(current, never()).forceReconnect()
    }

    @Test
    fun destroyedActivityIgnoresQueuedStatusAndBindingRetry() {
        val current = service(connected = true)
        WebSocketService.instance = current
        wire()
        current.statusCallbackOffline!!.invoke()
        doReturn(true).whenever(activity).isDestroyed
        clearInvocations(toolbar)
        ui.forEach(Runnable::run)
        wire()
        verify(toolbar, never()).setSubtitle(any<CharSequence>())
    }

    private fun wire() {
        MainActivity::class.java.getDeclaredMethod("wireConnectionStatusCallbacks")
            .apply { isAccessible = true }.invoke(activity)
    }

    private fun service(connected: Boolean): WebSocketService =
        Mockito.mock(WebSocketService::class.java, Answers.CALLS_REAL_METHODS).also {
            setField(it, "isConnected", connected)
        }

    private fun setField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
}

package com.securecall.app.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallLifecycleTest {
    @Test
    fun `ended session remains suppressed when its duplicate delivery arrives`() {
        val ended = EndedIncomingCalls()
        ended.remember("call-1")
        ended.remember("call-1")
        ended.remember("")
        assertTrue(ended.contains("call-1"))
        assertFalse(ended.contains("call-2"))
        assertFalse(ended.contains(""))
    }

    @Test
    fun `ended call history is bounded and evicts the oldest session`() {
        val ended = EndedIncomingCalls(capacity = 2)
        ended.remember("call-1")
        ended.remember("call-2")
        ended.remember("call-2")
        assertTrue(ended.contains("call-1"))
        ended.remember("call-3")
        assertFalse(ended.contains("call-1"))
        assertTrue(ended.contains("call-2"))
        assertTrue(ended.contains("call-3"))
    }

    @Test
    fun `Telecom uses alerting notification without a second service ringtone`() {
        assertFalse(IncomingCallAlertPolicy.serviceHandlesRingtone(true, true))
        assertFalse(IncomingCallAlertPolicy.serviceHandlesRingtone(true, false))
    }

    @Test
    fun `fallback retains service ringtone or uses notification if service unavailable`() {
        assertTrue(IncomingCallAlertPolicy.serviceHandlesRingtone(false, true))
        assertFalse(IncomingCallAlertPolicy.serviceHandlesRingtone(false, false))
    }

    @Test
    fun `binding must complete before showing or answering`() {
        val call = IncomingCallLifecycle()
        assertFalse(call.showUi())
        assertFalse(call.answer())
        assertTrue(call.shouldTimeout())
        assertTrue(call.attach())
        assertTrue(call.showUi())
    }

    @Test
    fun `duplicate deliveries and UI callbacks do not restart ringing`() {
        val call = IncomingCallLifecycle()
        assertTrue(call.attach())
        assertFalse(call.attach())
        assertTrue(call.showUi())
        assertFalse(call.showUi())
        assertTrue(call.answer())
        assertFalse(call.showUi())
        assertFalse(call.answer())
        assertFalse(call.shouldTimeout())
    }

    @Test
    fun `remote end before binding rejects the late connection and UI`() {
        val call = IncomingCallLifecycle()
        assertTrue(call.end())
        assertFalse(call.attach())
        assertFalse(call.showUi())
        assertFalse(call.answer())
        assertFalse(call.shouldTimeout())
    }

    @Test
    fun `remote end or decline while ringing prevents a late answer`() {
        val call = IncomingCallLifecycle()
        call.attach()
        call.showUi()
        assertTrue(call.end())
        assertFalse(call.end())
        assertFalse(call.answer())
        assertFalse(call.isActive())
    }

    @Test
    fun `Telecom can answer from its conflict UI before showing app UI`() {
        val call = IncomingCallLifecycle()
        call.attach()
        assertTrue(call.answer())
        assertTrue(call.isActive())
        assertFalse(call.showUi())
        assertFalse(call.shouldTimeout())
        assertTrue(call.end())
        assertFalse(call.isActive())
    }

    @Test
    fun `deadline applies while binding and ringing but never after answer or end`() {
        val call = IncomingCallLifecycle()
        assertTrue(call.shouldTimeout())
        call.attach()
        assertTrue(call.shouldTimeout())
        call.showUi()
        assertTrue(call.shouldTimeout())
        call.answer()
        assertFalse(call.shouldTimeout())
        call.end()
        assertFalse(call.shouldTimeout())
    }
}

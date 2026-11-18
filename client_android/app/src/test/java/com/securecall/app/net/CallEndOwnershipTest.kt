package com.securecall.app.net

import com.securecall.app.crypto.MediaSessionV3
import com.securecall.app.notifications.IncomingCallNotifications
import com.securecall.app.security.VerifiedCallInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * SC245 call-end ownership: drives the real CALL_END, grace re-entry, adoption and
 * clear paths of [WebSocketService] with synthetic ids only (no native, Telecom or network).
 */
class CallEndOwnershipTest {
    private val a = "00000000-0000-4000-8000-0000000000a1"
    private val b = "00000000-0000-4000-8000-0000000000b2"

    private fun set(target: WebSocketService, field: String, value: Any?) {
        WebSocketService::class.java.getDeclaredField(field).apply { isAccessible = true }.set(target, value)
    }

    private fun get(target: WebSocketService, field: String): Any? =
        WebSocketService::class.java.getDeclaredField(field).apply { isAccessible = true }.get(target)

    private fun service(): WebSocketService {
        val target = Mockito.mock(WebSocketService::class.java, Answers.CALLS_REAL_METHODS)
        set(target, "audioPlaybackLifecycle", AudioPlaybackLifecycle())
        set(target, "pendingCallQueue", mutableListOf<() -> Unit>())
        return target
    }

    private fun invite(id: String) = VerifiedCallInvite(
        id, "sc-caller", "sc-" + "C".repeat(43), "sc-callee", "sc-callee", "A".repeat(43),
        "synthetic-identity", 2_000_000_000L, "N".repeat(22), "synthetic-signature", "synthetic-digest",
    )

    private fun securityState(id: String): Any {
        val stateClass = Class.forName("com.securecall.app.net.CallSecurityState")
        val roleClass = Class.forName("com.securecall.app.net.SecureCallRole")
        val roleValue = roleClass.enumConstants.first { (it as Enum<*>).name == "CALLEE" }
        val constructor = stateClass.declaredConstructors.first {
            it.parameterCount == 10 && it.parameterTypes[0] == roleClass
        }.apply { isAccessible = true }
        return constructor.newInstance(roleValue, invite(id), "synthetic-transcript", null, null, null,
            false, false, false, null)
    }

    private fun callEnd(target: WebSocketService, id: String, reason: String = "user_hangup") {
        WebSocketService::class.java.getDeclaredMethod("handleIncomingCallEnd", String::class.java)
            .apply { isAccessible = true }
            .invoke(target, """{"type":"CALL_END","sessionId":"$id","reason":"$reason"}""")
    }

    private fun executeCallEnd(target: WebSocketService, id: String) {
        WebSocketService::class.java.getDeclaredMethod(
            "executeCallEnd", String::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(target, id, false)
    }

    private fun adopt(target: WebSocketService, inv: VerifiedCallInvite, fromFcm: Boolean): Boolean =
        WebSocketService::class.java.getDeclaredMethod(
            "acceptAuthenticatedInvite", VerifiedCallInvite::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(target, inv, fromFcm) as Boolean

    private fun owned(target: WebSocketService, id: String) {
        set(target, "_currentSessionId", id)
        set(target, "callSecurityState", securityState(id))
    }

    @Test
    fun `stale CALL_END for A leaves active B id security callbacks untouched`() {
        val ws = service()
        owned(ws, b)
        val bState = get(ws, "callSecurityState")
        val ended = Collections.synchronizedList(mutableListOf<String>())
        val errors = Collections.synchronizedList(mutableListOf<String>())
        set(ws, "_onCallEnded", { id: String -> ended.add(id); Unit })
        set(ws, "_onCallError", { code: String, _: String -> errors.add(code); Unit })
        callEnd(ws, a, "media_upgrade_required")
        callEnd(ws, a, "peer_disconnected")
        callEnd(ws, "", "user_hangup")
        assertEquals(b, ws.getCurrentSessionId())
        assertSame(bState, get(ws, "callSecurityState"))
        assertTrue(ended.isEmpty())
        assertTrue(errors.isEmpty())
        assertNull(get(ws, "callEndGraceRunnable"))
        assertNull(get(ws, "closingCallSessionId"))
    }

    @Test
    fun `aliased old security or fcm id cannot close a newer current session`() {
        val ws = service()
        set(ws, "_currentSessionId", b)
        set(ws, "callSecurityState", securityState(a))
        set(ws, "fcmPendingSessionId", a)
        callEnd(ws, a)
        assertEquals(b, ws.getCurrentSessionId())
        assertEquals(a, get(ws, "fcmPendingSessionId"))
    }

    @Test
    fun `matching end closes once and reports once`() {
        val ws = service()
        owned(ws, a)
        val ended = Collections.synchronizedList(mutableListOf<String>())
        set(ws, "_onCallEnded", { id: String -> ended.add(id); Unit })
        callEnd(ws, a)
        callEnd(ws, a)
        assertNull(ws.getCurrentSessionId())
        assertNull(get(ws, "callSecurityState"))
        assertEquals(listOf(a), ended)
        assertNull(get(ws, "closingCallSessionId"))
    }

    @Test
    fun `pending security and fcm fallbacks close only without a stronger owner`() {
        val ws = service()
        set(ws, "callSecurityState", securityState(a))
        callEnd(ws, a)
        assertNull(get(ws, "callSecurityState"))
        set(ws, "fcmPendingSessionId", b)
        val ended = mutableListOf<String>()
        set(ws, "_onCallEnded", { id: String -> ended.add(id); Unit })
        callEnd(ws, b)
        assertNull(get(ws, "fcmPendingSessionId"))
        assertEquals(listOf(b), ended)
    }

    @Test
    fun `stale grace re-entry after B adoption is a no-op for B`() {
        val ws = service()
        owned(ws, a)
        callEnd(ws, a) // A closed
        assertTrue(adopt(ws, invite(b), fromFcm = true))
        val bState = get(ws, "callSecurityState")
        val ended = mutableListOf<String>()
        set(ws, "_onCallEnded", { id: String -> ended.add(id); Unit })
        executeCallEnd(ws, a) // what A's grace runnable would do after 15 s
        assertEquals(b, ws.getCurrentSessionId())
        assertEquals(b, get(ws, "fcmPendingSessionId"))
        assertSame(bState, get(ws, "callSecurityState"))
        assertTrue(ended.isEmpty())
    }

    @Test
    fun `upgrade end reports the captured owner callback despite reentrant replacement`() {
        val ws = service()
        owned(ws, a)
        val aErrors = mutableListOf<String>()
        val bErrors = mutableListOf<String>()
        val aEnded = mutableListOf<String>()
        val media = Mockito.mock(MediaSessionV3::class.java)
        Mockito.doAnswer {
            // A screen replaced/cleared callbacks while A's resources were closing.
            set(ws, "_onCallError", { code: String, _: String -> bErrors.add(code); Unit })
            set(ws, "_onCallEnded", null)
        }.`when`(media).close()
        set(ws, "mediaSession", media)
        set(ws, "_onCallError", { code: String, _: String -> aErrors.add(code); Unit })
        set(ws, "_onCallEnded", { id: String -> aEnded.add(id); Unit })
        callEnd(ws, a, "media_upgrade_required")
        assertEquals(listOf("peer_media_upgrade_required"), aErrors)
        assertTrue(bErrors.isEmpty())
        assertTrue(aEnded.isEmpty()) // the existing error display ends the screen
        Mockito.verify(media, Mockito.times(1)).close()
    }

    @Test
    fun `paused A close blocks B adoption until A released and B survives`() {
        val ws = service()
        owned(ws, a)
        val closing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val media = Mockito.mock(MediaSessionV3::class.java)
        Mockito.doAnswer { closing.countDown(); release.await(5, TimeUnit.SECONDS); Unit }.`when`(media).close()
        set(ws, "mediaSession", media)
        val closer = Thread { callEnd(ws, a) }.apply { start() }
        assertTrue(closing.await(5, TimeUnit.SECONDS))
        assertEquals(a, get(ws, "closingCallSessionId"))
        var adopted = false
        val adopter = Thread { adopted = adopt(ws, invite(b), fromFcm = false) }.apply { start() }
        adopter.join(200)
        assertTrue("B adoption must wait for the A close reservation", adopter.isAlive)
        release.countDown()
        closer.join(5_000)
        adopter.join(5_000)
        assertFalse(closer.isAlive || adopter.isAlive)
        assertTrue(adopted)
        assertEquals(b, ws.getCurrentSessionId())
        assertNull(get(ws, "closingCallSessionId"))
        assertNull(get(ws, "closingCallThread"))
    }

    @Test
    fun `clear for an old owner never nulls the replacement and activity helpers stay scoped`() {
        val ws = service()
        owned(ws, b)
        assertFalse(ws.clearSessionIfOwner(a))
        assertFalse(ws.isCallOwnerOrIdle(a))
        assertTrue(ws.isCallOwnerOrIdle(b))
        assertEquals(b, ws.getCurrentSessionId())
        val mine: (String) -> Unit = {}
        val newer: (String) -> Unit = {}
        set(ws, "_onCallEnded", newer)
        ws.clearCallbacksIfOwned(mine, null, null, null)
        assertSame(newer, get(ws, "_onCallEnded"))
        assertTrue(ws.clearSessionIfOwner(b))
        assertTrue(ws.isCallOwnerOrIdle(null))
    }

    @Test
    fun `notification tag and pending intent identity differ per session`() {
        assertNotEquals(IncomingCallNotifications.tag(a), IncomingCallNotifications.tag(b))
        assertNotEquals(IncomingCallNotifications.requestCode(a), IncomingCallNotifications.requestCode(b))
        assertEquals(IncomingCallNotifications.tag(a), IncomingCallNotifications.tag(a))
    }
}

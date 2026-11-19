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

    private fun securityState(id: String, role: String = "CALLEE"): Any {
        val stateClass = Class.forName("com.securecall.app.net.CallSecurityState")
        val roleClass = Class.forName("com.securecall.app.net.SecureCallRole")
        val roleValue = roleClass.enumConstants.first { (it as Enum<*>).name == role }
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

    // ---- SC245 owner correction (P1-P7) ----

    private fun bind(target: WebSocketService, owner: String, ended: ((String) -> Unit)?, error: ((String, String) -> Unit)?) =
        target.bindCallCallbacks(owner, ended, error)

    private fun reportOwnedError(target: WebSocketService, id: String, code: String) {
        WebSocketService::class.java.getDeclaredMethod(
            "reportOwnedError", String::class.java, String::class.java, String::class.java,
        ).apply { isAccessible = true }.invoke(target, id, code, "synthetic")
    }

    private fun withReservation(target: WebSocketService, id: String?, requireOwner: Boolean, wait: Boolean, effect: () -> Any?): Any? =
        WebSocketService::class.java.getDeclaredMethod(
            "withCallReservation", String::class.java, Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Function0::class.java,
        ).apply { isAccessible = true }.invoke(target, id, requireOwner, wait, effect)

    private fun blockingMedia(entered: CountDownLatch, release: CountDownLatch): MediaSessionV3 {
        val media = Mockito.mock(MediaSessionV3::class.java)
        Mockito.doAnswer { entered.countDown(); release.await(10, TimeUnit.SECONDS); Unit }.`when`(media).close()
        return media
    }

    @Test
    fun `B adoption is rejected when A close outlives the bound and nothing is mutated`() {
        val ws = service()
        owned(ws, a)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        set(ws, "mediaSession", blockingMedia(entered, release))
        val closer = Thread { callEnd(ws, a) }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val started = System.currentTimeMillis()
        val adopted = adopt(ws, invite(b), fromFcm = true) // waits the 2 s bound, then fails closed
        assertTrue(System.currentTimeMillis() - started >= 1_900)
        assertFalse(adopted)
        assertNull(ws.getCurrentSessionId())
        assertNull(get(ws, "callSecurityState"))
        assertNull(get(ws, "fcmPendingSessionId"))
        assertNull(get(ws, "remotePubKey"))
        release.countDown()
        closer.join(5_000)
        assertFalse(closer.isAlive)
        assertNull(get(ws, "closingCallSessionId"))
        assertNull(ws.getCurrentSessionId()) // never adopted later
    }

    @Test
    fun `same-thread reentry during A close rejects B adoption and bind`() {
        val ws = service()
        owned(ws, a)
        var reentrantAdopt: Boolean? = null
        var reentrantBind: Boolean? = null
        val media = Mockito.mock(MediaSessionV3::class.java)
        Mockito.doAnswer {
            assertFalse("media closes outside the service monitor", Thread.holdsLock(ws))
            reentrantAdopt = adopt(ws, invite(b), fromFcm = false)
            reentrantBind = bind(ws, b, {}, null)
            Unit
        }.`when`(media).close()
        set(ws, "mediaSession", media)
        set(ws, "callCallbackOwner", a)
        set(ws, "_onCallEnded", { _: String -> Unit })
        callEnd(ws, a)
        assertEquals(false, reentrantAdopt)
        assertEquals(false, reentrantBind)
        assertNull(ws.getCurrentSessionId())
        assertNull(get(ws, "callSecurityState"))
        assertNull(get(ws, "closingCallSessionId"))
    }

    @Test
    fun `reservation spans the effect so adoption and a second reservation cannot interleave`() {
        val ws = service()
        val inEffect = CountDownLatch(1)
        val release = CountDownLatch(1)
        var reservedDuringEffect: Any? = "unset"
        val effectThread = Thread {
            withReservation(ws, null, requireOwner = false, wait = true) {
                inEffect.countDown(); release.await(5, TimeUnit.SECONDS); "done"
            }
        }.apply { start() }
        assertTrue(inEffect.await(5, TimeUnit.SECONDS))
        assertTrue("idle reservation has a non-null identity", get(ws, "closingCallSessionId") != null)
        var adopted = false
        val adopter = Thread { adopted = adopt(ws, invite(b), fromFcm = false) }.apply { start() }
        Thread { reservedDuringEffect = withReservation(ws, null, requireOwner = false, wait = false) { "x" } }
            .apply { start(); join(2_000) }
        adopter.join(200)
        assertTrue("adoption must wait for the effect", adopter.isAlive)
        assertNull(ws.getCurrentSessionId())
        assertNull(reservedDuringEffect)
        release.countDown()
        effectThread.join(5_000)
        adopter.join(5_000)
        assertTrue(adopted)
        assertEquals(b, ws.getCurrentSessionId())
        assertNull(get(ws, "closingCallSessionId"))
    }

    @Test
    fun `stale A audio helpers never stop B ringtone`() {
        val ws = service()
        owned(ws, b)
        val player = Mockito.mock(android.media.MediaPlayer::class.java)
        set(ws, "incomingRingtone", player)
        ws.stopIncomingRingtoneIfOwner(a)
        ws.killAllAudioIfOwner(a)
        assertSame(player, get(ws, "incomingRingtone"))
        Mockito.verify(player, Mockito.never()).stop()
        ws.stopIncomingRingtoneIfOwner(b)
        assertNull(get(ws, "incomingRingtone"))
        Mockito.verify(player, Mockito.times(1)).stop()
        assertNull(get(ws, "closingCallSessionId"))
    }

    @Test
    fun `B binding cannot steal live A callbacks and A end reaches only A`() {
        val ws = service()
        owned(ws, a)
        val aEnded = mutableListOf<String>()
        val bEnded = mutableListOf<String>()
        val bErrors = mutableListOf<String>()
        val aEnd: (String) -> Unit = { aEnded.add(it) }
        assertTrue(bind(ws, a, aEnd, null))
        assertFalse(bind(ws, b, { bEnded.add(it) }, { c, _ -> bErrors.add(c) }))
        assertSame(aEnd, get(ws, "_onCallEnded"))
        callEnd(ws, a)
        callEnd(ws, a)
        assertEquals(listOf(a), aEnded)
        assertTrue(bEnded.isEmpty() && bErrors.isEmpty())
        assertTrue(bind(ws, b, { bEnded.add(it) }, { c, _ -> bErrors.add(c) }))
        callEnd(ws, a) // stale end after B bound
        executeCallEnd(ws, a)
        // A late A WebRTC disconnect resolves its ended callback by owner: none for B's binding.
        val lateA = WebSocketService::class.java.getDeclaredMethod("endedCallbackFor", String::class.java)
            .apply { isAccessible = true }
        assertNull(lateA.invoke(ws, a))
        assertTrue(lateA.invoke(ws, b) != null)
        assertTrue(bEnded.isEmpty() && bErrors.isEmpty())
    }

    @Test
    fun `A failed send after B adopted never detaches B or reaches B callbacks`() {
        val ws = service()
        owned(ws, b)
        val bState = get(ws, "callSecurityState")
        val bErrors = mutableListOf<String>()
        assertTrue(bind(ws, b, {}, { c, _ -> bErrors.add(c) }))
        // The not-sent / pre-admission branches use exactly these owner-scoped helpers.
        assertFalse(ws.clearSessionIfOwner(a))
        reportOwnedError(ws, a, "not_connected")
        reportOwnedError(ws, a, "invalid_call_target")
        assertEquals(b, ws.getCurrentSessionId())
        assertSame(bState, get(ws, "callSecurityState"))
        assertTrue(bErrors.isEmpty())
        reportOwnedError(ws, b, "busy")
        assertEquals(listOf("busy"), bErrors)
    }

    @Test
    fun `queued A request ended before flush never starts even when idle`() {
        val ws = service()
        set(ws, "isRegistered", false)
        ws.sendCallInvite("synthetic-target", a)
        @Suppress("UNCHECKED_CAST")
        val queue = get(ws, "pendingCallQueue") as MutableList<() -> Unit>
        assertEquals(1, queue.size)
        val copied = queue.toList() // an in-flight copy taken by a flush
        assertFalse(ws.clearSessionIfOwner(a)) // nothing admitted; the request is cancelled
        @Suppress("UNCHECKED_CAST")
        assertTrue((get(ws, "pendingInviteOwners") as MutableSet<String>).isEmpty())
        set(ws, "isRegistered", true) // B ended meanwhile, service idle and registered
        copied.forEach { it.invoke() } // would reach target resolution/keys if it started
        queue.toList().forEach { it.invoke() }
        assertNull(ws.getCurrentSessionId())
        assertNull(get(ws, "callSecurityState"))
        assertNull(get(ws, "localPrivKey"))
    }

    @Test
    fun `delayed ringtone effect requires the same still-ringing unaccepted owner`() {
        val ws = service()
        owned(ws, b)
        assertFalse(ws.isRingingOwner(a))
        assertFalse(ws.startIncomingRingtoneIfRinging(a))
        assertNull(get(ws, "incomingRingtone"))
        callEnd(ws, b) // B ended -> idle; a delayed B effect must not ring
        assertTrue(com.securecall.app.call.IncomingCallTelecom.isEnded(b))
        assertFalse(ws.isRingingOwner(b))
        assertFalse(ws.startIncomingRingtoneIfRinging(b))
        owned(ws, a)
        val stateClass = Class.forName("com.securecall.app.net.CallSecurityState")
        val claim = stateClass.getDeclaredMethod("getAcceptClaim").apply { isAccessible = true }.invoke(get(ws, "callSecurityState"))
            as java.util.concurrent.atomic.AtomicBoolean
        assertTrue(ws.isRingingOwner(a))
        claim.set(true) // A accepted locally: must not ring again
        assertFalse(ws.isRingingOwner(a))
        assertFalse(ws.startIncomingRingtoneIfRinging(a))
        assertNull(get(ws, "closingCallSessionId"))
    }

    private fun awaitReservationWait(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (thread.isAlive && thread.state != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
            Thread.sleep(1)
        }
        assertEquals("must reach the service reservation wait", Thread.State.TIMED_WAITING, thread.state)
    }

    private fun releaseReservation(target: WebSocketService, id: String) {
        WebSocketService::class.java.getDeclaredMethod("releaseCallReservation", String::class.java)
            .apply { isAccessible = true }.invoke(target, id)
    }

    private fun admitOutgoing(target: WebSocketService, id: String, key: ByteArray): Boolean? =
        WebSocketService::class.java.getDeclaredMethod(
            "admitOutgoingInvite", String::class.java, ByteArray::class.java,
            Class.forName("com.securecall.app.net.CallSecurityState"),
        ).apply { isAccessible = true }.invoke(target, id, key, securityState(id, "CALLER")) as Boolean?

    private fun armOutgoing(target: WebSocketService, id: String): Boolean =
        WebSocketService::class.java.getDeclaredMethod("armOutgoingInvite", String::class.java)
            .apply { isAccessible = true }.invoke(target, id) as Boolean

    @Test
    fun `ACK waiting behind A effect reads B state only after the wait`() {
        val ws = service()
        owned(ws, a)
        set(ws, "callSecurityState", securityState(a, "CALLER"))
        val messages = WebSocketService::class.java.getDeclaredMethod("handleIncomingMessage", String::class.java)
            .apply { isAccessible = true }
        val ack = Thread { messages.invoke(ws, """{"type":"CALL_INVITE_ACK","ok":true,"protocol":3,"sessionId":"$a"}""") }
        val ended = mutableListOf<String>()
        val player = Mockito.mock(android.media.MediaPlayer::class.java)
        var bState: Any? = null
        withReservation(ws, a, requireOwner = true, wait = true) {
            ack.start()
            awaitReservationWait(ack)
            // Force close/adopt to win the released monitor before the queued ACK resumes.
            synchronized(ws) {
                releaseReservation(ws, a)
                callEnd(ws, a)
                assertTrue(adopt(ws, invite(b), fromFcm = true))
                assertTrue(bind(ws, b, { ended.add(it) }, null))
                set(ws, "incomingRingtone", player)
                bState = get(ws, "callSecurityState")
            }
        }
        ack.join(3_000)
        assertFalse(ack.isAlive)
        assertEquals(b, ws.getCurrentSessionId())
        assertSame(bState, get(ws, "callSecurityState"))
        assertSame(player, get(ws, "incomingRingtone"))
        assertTrue(ended.isEmpty())
        Mockito.verify(player, Mockito.never()).stop()
    }

    @Test
    fun `matching caller ACK remains admitted`() {
        val ws = service()
        set(ws, "callSecurityState", securityState(a, "CALLER"))
        WebSocketService::class.java.getDeclaredMethod("handleIncomingMessage", String::class.java)
            .apply { isAccessible = true }
            .invoke(ws, """{"type":"CALL_INVITE_ACK","ok":true,"protocol":3,"sessionId":"$a"}""")
        assertEquals(a, ws.getCurrentSessionId())
    }

    @Test
    fun `cancel during outgoing admission wait wipes local key and never adopts after idle`() {
        val ws = service()
        owned(ws, b)
        set(ws, "pendingInviteOwners", mutableSetOf(a))
        val key = ByteArray(32) { 7 }
        var admitted: Any? = "not returned"
        val waiter = Thread { admitted = admitOutgoing(ws, a, key) }
        withReservation(ws, b, requireOwner = true, wait = true) {
            waiter.start()
            awaitReservationWait(waiter)
            assertFalse(ws.clearSessionIfOwner(a))
            waiter.join(3_000) // cancel wakes the real wait even while B's effect remains reserved
            assertFalse(waiter.isAlive)
            assertNull(admitted)
            assertTrue(key.all { it == 0.toByte() })
            assertNull(get(ws, "localPrivKey"))
        }
        callEnd(ws, b)
        assertNull(ws.getCurrentSessionId())
        assertNull(get(ws, "callSecurityState"))
        assertNull(get(ws, "localPrivKey"))
    }

    @Test
    fun `live outgoing admission consumes only success and reentry wipes a rejected key`() {
        val ws = service()
        set(ws, "pendingInviteOwners", mutableSetOf(a))
        val rejectedKey = ByteArray(32) { 7 }
        withReservation(ws, null, requireOwner = false, wait = true) {
            assertEquals(false, admitOutgoing(ws, a, rejectedKey))
            assertTrue(rejectedKey.all { it == 0.toByte() })
            assertNull(get(ws, "callSecurityState"))
            assertEquals(setOf(a), get(ws, "pendingInviteOwners"))
        }
        val admittedKey = ByteArray(32) { 8 }
        assertEquals(true, admitOutgoing(ws, a, admittedKey))
        assertSame(admittedKey, get(ws, "localPrivKey"))
        assertTrue((get(ws, "pendingInviteOwners") as Set<*>).isEmpty())
        assertTrue(get(ws, "callSecurityState") != null)
    }

    @Test
    fun `late successful A send tail leaves B active state and timer untouched`() {
        val ws = service()
        owned(ws, a)
        val handler = Mockito.mock(android.os.Handler::class.java)
        val client = Mockito.mock(HeartbeatClient::class.java)
        set(ws, "outgoingRingHandler", handler)
        set(ws, "client", client)
        val sendEntered = CountDownLatch(1)
        val sendReturned = CountDownLatch(1)
        var armed: Boolean? = null
        val sender = Thread {
            sendEntered.countDown()
            sendReturned.await(5, TimeUnit.SECONDS)
            armed = armOutgoing(ws, a) // exact successful-send continuation used by dispatch
        }.apply { start() }
        assertTrue(sendEntered.await(2, TimeUnit.SECONDS))
        callEnd(ws, a)
        assertTrue(adopt(ws, invite(b), fromFcm = false))
        assertTrue(armOutgoing(ws, b))
        val bTimer = get(ws, "outgoingRingTimeout")
        val bState = get(ws, "callSecurityState")
        Mockito.clearInvocations(client, handler)
        sendReturned.countDown()
        sender.join(3_000)
        assertFalse(sender.isAlive)
        assertEquals(false, armed)
        assertSame(bTimer, get(ws, "outgoingRingTimeout"))
        assertSame(bState, get(ws, "callSecurityState"))
        assertEquals(b, ws.getCurrentSessionId())
        Mockito.verifyNoInteractions(client, handler)
    }

    @Test
    fun `valid send tail reserves ownership across heartbeat and timer effects`() {
        val ws = service()
        owned(ws, a)
        val handler = Mockito.mock(android.os.Handler::class.java)
        val client = Mockito.mock(HeartbeatClient::class.java)
        set(ws, "outgoingRingHandler", handler)
        set(ws, "client", client)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Mockito.doAnswer {
            assertFalse(Thread.holdsLock(ws))
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            Unit
        }.`when`(client).setCallActive(true)
        var armed: Boolean? = null
        val sender = Thread { armed = armOutgoing(ws, a) }.apply { start() }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        assertNull(withReservation(ws, b, requireOwner = false, wait = false) { "must not run" })
        assertNull(get(ws, "outgoingRingTimeout"))
        release.countDown()
        sender.join(3_000)
        assertFalse(sender.isAlive)
        assertEquals(true, armed)
        val timer = get(ws, "outgoingRingTimeout") as Runnable
        Mockito.verify(handler).postDelayed(timer, com.securecall.app.call.RingTimeoutPolicy.OUTGOING_RING_TIMEOUT_MS)
        assertNull(get(ws, "closingCallSessionId"))
    }

    @Test
    fun `cold FCM post-check withdraws only A when B service appeared during posting`() {
        val oldInstance = WebSocketService.instance
        val postedA = "00000000-0000-4000-8000-0000000000c1"
        val liveB = "00000000-0000-4000-8000-0000000000c2"
        val cancelled = mutableListOf<String>()
        try {
            WebSocketService.instance = null
            assertTrue(WebSocketService.admitFcmUiEffect(postedA, "synthetic", true))
            val ws = service()
            owned(ws, liveB)
            val bState = get(ws, "callSecurityState")
            Mockito.doReturn(false).`when`(ws).acceptFcmInvite("synthetic")
            WebSocketService.instance = ws // notification post returns after a new service owns B
            assertFalse(WebSocketService.admitFcmUiEffect(postedA, "synthetic", true) {
                cancelled.add(IncomingCallNotifications.tag(postedA))
            })
            assertEquals(listOf(IncomingCallNotifications.tag(postedA)), cancelled)
            assertFalse(cancelled.contains(IncomingCallNotifications.tag(liveB)))
            assertEquals(liveB, ws.getCurrentSessionId())
            assertSame(bState, get(ws, "callSecurityState"))
        } finally {
            WebSocketService.instance = oldInstance
        }
    }

    @Test
    fun `FCM post-check preserves absent and ringing paths but withdraws accepted or ended`() {
        val oldInstance = WebSocketService.instance
        val id = "00000000-0000-4000-8000-0000000000c3"
        val cancelled = mutableListOf<String>()
        val withdraw = { cancelled.add(IncomingCallNotifications.tag(id)); Unit }
        try {
            WebSocketService.instance = null
            assertTrue(WebSocketService.admitFcmUiEffect(id, "synthetic", true, withdraw))
            val ws = service()
            owned(ws, id)
            WebSocketService.instance = ws
            assertTrue(WebSocketService.admitFcmUiEffect(id, "synthetic", false, withdraw))
            val stateClass = Class.forName("com.securecall.app.net.CallSecurityState")
            val claim = stateClass.getDeclaredMethod("getAcceptClaim").apply { isAccessible = true }
                .invoke(get(ws, "callSecurityState")) as java.util.concurrent.atomic.AtomicBoolean
            claim.set(true)
            assertFalse(WebSocketService.admitFcmUiEffect(id, "synthetic", false, withdraw))
            com.securecall.app.call.IncomingCallTelecom.end(id)
            WebSocketService.instance = null
            assertFalse(WebSocketService.admitFcmUiEffect(id, "synthetic", true, withdraw))
            assertEquals(listOf(IncomingCallNotifications.tag(id), IncomingCallNotifications.tag(id)), cancelled)
        } finally {
            WebSocketService.instance = oldInstance
        }
    }

    @Test
    fun `delayed accept ACK for A leaves replacement B ringing and playing`() {
        val ws = service()
        owned(ws, a)
        callEnd(ws, a)
        assertTrue(adopt(ws, invite(b), fromFcm = false))
        val state = get(ws, "callSecurityState")
        val lifecycle = get(ws, "audioPlaybackLifecycle") as AudioPlaybackLifecycle
        val playback = lifecycle.snapshot()
        val ringtone = Mockito.mock(android.media.MediaPlayer::class.java)
        val player = Mockito.mock(com.securecall.app.ghostnet.media.playback.GhostAudioPlayer::class.java)
        val ended = mutableListOf<String>()
        val errors = mutableListOf<String>()
        assertTrue(bind(ws, b, { ended.add(it) }, { code, _ -> errors.add(code) }))
        set(ws, "incomingRingtone", ringtone)
        set(ws, "audioPlayer", player)
        set(ws, "jitterPlaybackRunning", true)
        Mockito.clearInvocations(ws)

        ws.onMessage("""{"type":"CALL_ACCEPT_ACK","protocol":3,"ok":true,"sessionId":"$a"}""")

        assertEquals(b, ws.getCurrentSessionId())
        assertSame(state, get(ws, "callSecurityState"))
        assertSame(ringtone, get(ws, "incomingRingtone"))
        assertSame(player, get(ws, "audioPlayer"))
        assertEquals(true, get(ws, "jitterPlaybackRunning"))
        assertTrue(lifecycle.consume(playback) {})
        assertTrue(ended.isEmpty() && errors.isEmpty())
        Mockito.verify(ws, Mockito.never()).killAllAudio()
        Mockito.verifyNoInteractions(ringtone, player)
    }

    @Test
    fun `valid current accept ACK after playback starts is informational`() {
        val ws = service()
        owned(ws, a)
        val state = get(ws, "callSecurityState")!!
        state.javaClass.getDeclaredMethod("getAcceptClaim").apply { isAccessible = true }
            .invoke(state).let { (it as java.util.concurrent.atomic.AtomicBoolean).set(true) }
        state.javaClass.getDeclaredMethod("setEstablished", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(state, true)
        val lifecycle = get(ws, "audioPlaybackLifecycle") as AudioPlaybackLifecycle
        val playback = lifecycle.snapshot()
        val player = Mockito.mock(com.securecall.app.ghostnet.media.playback.GhostAudioPlayer::class.java)
        set(ws, "audioPlayer", player)
        set(ws, "jitterPlaybackRunning", true)

        repeat(2) {
            ws.onMessage("""{"type":"CALL_ACCEPT_ACK","protocol":3,"ok":true,"sessionId":"$a"}""")
        }

        assertEquals(a, ws.getCurrentSessionId())
        assertSame(state, get(ws, "callSecurityState"))
        assertTrue(ws.isSecureSessionEstablished())
        assertSame(player, get(ws, "audioPlayer"))
        assertEquals(true, get(ws, "jitterPlaybackRunning"))
        assertTrue(lifecycle.consume(playback) {})
        Mockito.verify(ws, Mockito.never()).killAllAudio()
        Mockito.verifyNoInteractions(player)
    }

    @Test
    fun `missing foreign and invalid accept ACK fields cannot trigger effects`() {
        val ws = service()
        owned(ws, b)
        val state = get(ws, "callSecurityState")
        val lifecycle = get(ws, "audioPlaybackLifecycle") as AudioPlaybackLifecycle
        val playback = lifecycle.snapshot()
        val ringtone = Mockito.mock(android.media.MediaPlayer::class.java)
        set(ws, "incomingRingtone", ringtone)
        val acknowledgements = listOf(
            """{"type":"CALL_ACCEPT_ACK","protocol":3,"ok":true}""",
            """{"type":"CALL_ACCEPT_ACK","protocol":3,"ok":true,"sessionId":""}""",
            """{"type":"CALL_ACCEPT_ACK","protocol":3,"ok":true,"sessionId":"$a"}""",
            """{"type":"CALL_ACCEPT_ACK","protocol":3,"ok":false,"sessionId":"$b"}""",
            """{"type":"CALL_ACCEPT_ACK","protocol":3,"sessionId":"$b"}""",
            """{"type":"CALL_ACCEPT_ACK","ok":true,"sessionId":"$b"}""",
            """{"type":"CALL_ACCEPT_ACK","protocol":2,"ok":true,"sessionId":"$b"}""",
            """{"type":"CALL_ACCEPT_ACK","protocol":"invalid","ok":true,"sessionId":"$b"}""",
        )
        acknowledgements.forEach { acknowledgement ->
            ws.onMessage(acknowledgement)
            assertEquals(b, ws.getCurrentSessionId())
            assertSame(state, get(ws, "callSecurityState"))
            assertSame(ringtone, get(ws, "incomingRingtone"))
            assertTrue(lifecycle.consume(playback) {})
        }
        Mockito.verify(ws, Mockito.never()).killAllAudio()
        Mockito.verifyNoInteractions(ringtone)
    }

    @Test
    fun `notification tag and pending intent identity differ per session`() {
        assertNotEquals(IncomingCallNotifications.tag(a), IncomingCallNotifications.tag(b))
        assertNotEquals(IncomingCallNotifications.requestCode(a), IncomingCallNotifications.requestCode(b))
        assertEquals(IncomingCallNotifications.tag(a), IncomingCallNotifications.tag(a))
    }
}

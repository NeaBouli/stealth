package com.securecall.app.net

import com.securecall.app.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.PeerConnection

/** SC-OWNED-CALL-CONFIG-20261119: owned-test network data never reaches default builds. */
class OwnedTestNetworkConfigTest {
    private val production = CertificatePinPolicy.PINS.map { CertificatePinPolicy.HOST to it }

    @Test
    fun `default build keeps production signaling stun and pins`() {
        if (BuildConfig.OWNED_TEST_PIN_HOST.isNotEmpty()) return // explicit owned-test debug run
        assertEquals("wss://api.stealthx.tech/signal", BuildConfig.SIGNAL_WS_URL)
        assertEquals("stun:stun.l.google.com:19302", BuildConfig.STUN_URL)
        assertEquals("", BuildConfig.OWNED_TEST_PIN)
        assertEquals(production, CertificatePinPolicy.entries())
    }

    @Test
    fun `owned pin is ignored outside debug or when incomplete`() {
        assertEquals(production, CertificatePinPolicy.entries(false, "10.0.2.2", OWNED_PIN))
        assertEquals(production, CertificatePinPolicy.entries(true, "", OWNED_PIN))
        assertEquals(production, CertificatePinPolicy.entries(true, "10.0.2.2", ""))
    }

    @Test
    fun `complete owned debug config adds exactly one owned host pin`() {
        assertEquals(production + ("10.0.2.2" to OWNED_PIN), CertificatePinPolicy.entries(true, "10.0.2.2", OWNED_PIN))
        assertEquals(production + ("127.0.0.1" to OWNED_PIN), CertificatePinPolicy.entries(true, "127.0.0.1", OWNED_PIN))
    }

    @Test
    fun `wrong owned host or malformed pin fails closed`() {
        listOf("api.stealthx.tech", "localhost", "10.0.2.3", "192.168.1.2", "example.com").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) { CertificatePinPolicy.entries(true, host, OWNED_PIN) }
        }
        listOf("sha1/abc=", "sha256/short=", OWNED_PIN.removePrefix("sha256/")).forEach { pin ->
            assertThrows(IllegalArgumentException::class.java) { CertificatePinPolicy.entries(true, "10.0.2.2", pin) }
        }
    }

    @Test
    fun `gradle refuses owned inputs without isolation on release or aggregate tasks`() {
        val gradle = File("build.gradle").readText()
        assertTrue(gradle.contains("if (!isolatedDeviceTest) refuse('requires -PisolatedDeviceTest')"))
        assertTrue(gradle.contains("!it.contains('debug') || it.contains('release') || it.contains('bundle')"))
        assertTrue(gradle.contains("def ownedHosts = ['10.0.2.2', '127.0.0.1']"))
        assertTrue(gradle.contains("signal.scheme != 'wss'"))
        assertTrue(gradle.contains("signal.rawQuery != null || signal.rawFragment != null || signal.rawPath != '/signal'"))
        assertTrue(gradle.contains("caPem.contains('PRIVATE KEY')"))
        assertTrue(gradle.contains("refuse('ownedTestPin must equal the SHA-256 SPKI pin of ownedTestCaCert')"))
        assertTrue(gradle.contains("if (ownedTestNetwork) debug.res.srcDir(ownedTestNetwork.resDir)"))
        assertFalse("no TURN input may exist", gradle.contains("ownedTestTurn"))
        val graphGuard = gradle.substringAfter("gradle.taskGraph.whenReady { graph ->").substringBefore("\n    }\n")
        assertTrue(graphGuard.contains("graph.allTasks.findAll"))
        assertTrue(graphGuard.contains("n.toLowerCase().contains('release') || n.endsWith('Bundle')"))
        assertTrue(graphGuard.contains("refuse(\"task graph selects Release/bundle tasks"))
        assertTrue("graph guard only inside owned block", gradle.indexOf("gradle.taskGraph.whenReady") > gradle.indexOf("ownedTestNetwork = [signalUrl"))

        val release = gradle.substringAfter("    buildTypes {").substringAfter("        release {").substringBefore("        debug {")
        assertTrue(release.contains("\"\\\"wss://api.stealthx.tech/signal\\\"\""))
        assertFalse("release build type must not read owned-test data", release.contains("ownedTest"))
    }

    @Test
    fun `no owned CA or owned host enters main or release source sets`() {
        listOf("src/main", "src/release", "src/free", "src/pro", "src/premium").forEach { dir ->
            File(dir).walkTopDown().filter { it.isFile }.forEach { file ->
                assertFalse("owned CA resource in $file", file.name.startsWith("owned_test_ca"))
            }
        }
        val mainNsc = File("src/main/res/xml/network_security_config.xml").readText()
        assertFalse(mainNsc.contains("10.0.2.2"))
        assertFalse(mainNsc.contains("owned_test_ca"))
    }

    @Test
    fun `owned dynamic ICE accepts only owned servers and keeps TURN credentials`() {
        val turn = PeerConnection.IceServer.builder("turn:10.0.2.2:3478?transport=udp")
            .setUsername("synthetic-user").setPassword("synthetic-pass").createIceServer()
        val public = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        val decide = { servers: List<PeerConnection.IceServer>?, relay: Boolean, host: String ->
            WebRtcManager.decideIceSource(servers, relay, host)
        }
        assertEquals(WebRtcManager.IceSourceDecision.DYNAMIC, decide(listOf(turn), true, "10.0.2.2"))
        assertEquals("synthetic-user", turn.username)
        assertEquals("synthetic-pass", turn.password)
        assertEquals(WebRtcManager.IceSourceDecision.REJECT_OWNED_ICE, decide(listOf(turn, public), false, "10.0.2.2"))
        assertEquals(WebRtcManager.IceSourceDecision.REJECT_OWNED_ICE, decide(listOf(public), true, "10.0.2.2"))
        // Empty owned ICE keeps the existing owned STUN/host-candidate fallback and relay-only rule.
        assertEquals(WebRtcManager.IceSourceDecision.STUN_FALLBACK, decide(null, false, "10.0.2.2"))
        assertEquals(WebRtcManager.IceSourceDecision.REJECT, decide(emptyList(), true, "10.0.2.2"))
        val ownedStunOnly = PeerConnection.IceServer.builder("stun:10.0.2.2:3478").createIceServer()
        assertEquals(WebRtcManager.IceSourceDecision.REJECT, decide(listOf(ownedStunOnly), true, "10.0.2.2"))
        // Default (owned mode off): ordinary dynamic ICE unchanged.
        assertEquals(WebRtcManager.IceSourceDecision.DYNAMIC, decide(listOf(public), false, ""))
        if (BuildConfig.OWNED_TEST_PIN_HOST.isEmpty()) {
            assertEquals(WebRtcManager.IceSourceDecision.DYNAMIC, WebRtcManager.decideIceSource(listOf(public), false))
        }
    }

    private companion object {
        const val OWNED_PIN = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}

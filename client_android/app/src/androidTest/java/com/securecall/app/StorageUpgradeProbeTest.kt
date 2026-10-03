package com.securecall.app

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.crypto.CoreCrypto
import java.io.File
import java.security.SecureRandom
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two-phase toolchain-upgrade probe for the Rust JNI crypto engine and persisted preferences.
 * Skipped unless `storageUpgradePhase` is passed:
 *
 *  1. build/install the PREVIOUS toolchain, run with `-e storageUpgradePhase write`
 *  2. `adb install -r` the NEW toolchain, run with `-e storageUpgradePhase read`
 *
 * The read phase must decrypt the ciphertext and re-derive the session key written by the
 * previous build. Only for emulators: it writes into the app's real preferences file.
 */
@RunWith(AndroidJUnit4::class)
class StorageUpgradeProbeTest {
    private val phase: String? =
        InstrumentationRegistry.getArguments().getString("storageUpgradePhase")
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker = File(context.filesDir, "storage-upgrade-probe.json")
    private val prefs = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)

    @Test
    fun cryptoAndPreferencesSurviveToolchainUpgrade() {
        assumeTrue("storageUpgradePhase not requested", phase == "write" || phase == "read")
        assertTrue("native crypto engine must load", CoreCrypto.isNativeAvailable())
        assertTrue("native crypto self-test must pass", CoreCrypto.selfTest())
        if (phase == "write") write() else read()
    }

    private fun write() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val plaintext = "storage-upgrade-probe".toByteArray(Charsets.UTF_8)
        val ciphertext = CoreCrypto.encrypt(key, plaintext)
        val local = CoreCrypto.generateKeyPair()
        val remote = CoreCrypto.generateKeyPair()
        val localPriv = local.copyOfRange(0, 32)
        val remotePub = remote.copyOfRange(32, 64)
        val sessionKey = CoreCrypto.deriveSessionKey(localPriv, remotePub)
        val prefValue = b64(key.copyOfRange(0, 8))
        prefs.edit().putString(PREF_KEY, prefValue).commit()
        marker.writeText(
            JSONObject()
                .put("key", b64(key))
                .put("plaintext", b64(plaintext))
                .put("ciphertext", b64(ciphertext))
                .put("localPriv", b64(localPriv))
                .put("remotePub", b64(remotePub))
                .put("sessionKey", b64(sessionKey))
                .put("pref", prefValue)
                .toString(),
        )
    }

    private fun read() {
        val json = JSONObject(marker.readText())
        val decrypted = CoreCrypto.decrypt(unb64(json.getString("key")), unb64(json.getString("ciphertext")))
        assertArrayEquals("ciphertext from the previous build must decrypt", unb64(json.getString("plaintext")), decrypted)
        val sessionKey = CoreCrypto.deriveSessionKey(unb64(json.getString("localPriv")), unb64(json.getString("remotePub")))
        assertArrayEquals("X25519 session key must be re-derived identically", unb64(json.getString("sessionKey")), sessionKey)
        assertEquals("preferences must stay readable", json.getString("pref"), prefs.getString(PREF_KEY, null))
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val PREF_KEY = "storage_upgrade_probe"
    }
}

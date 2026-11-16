package com.securecall.app.sc224

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.app.BuildConfig
import com.securecall.app.R
import com.securecall.app.SettingsActivity
import com.securecall.app.billing.DirectEntitlementStore
import com.securecall.app.config.FeatureProviderRegistry
import com.securecall.app.config.TierManager
import com.securecall.app.ui.SettingsFragment
import com.securecall.app.update.UpdateManager
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

private const val URL = "https://stealthx.tech/download.html"
private const val RECEIPT = "com.securecall.sc224.SYNTHETIC_RECEIVER"

@RunWith(AndroidJUnit4::class)
class Sc224Qualification {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store get() = DirectEntitlementStore(context)
    private val attempts = CopyOnWriteArrayList<Intent>()
    private val delivered = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            attempts.add(Intent(intent))
            return null // Observe, never intercept or replace the real OS launch.
        }
    }
    private val receipt = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            assertEquals(BuildConfig.APPLICATION_ID + ".test", intent.getStringExtra("receiverPackage"))
            delivered.add(requireNotNull(intent.getParcelableExtra("launched", Intent::class.java)))
        }
    }

    @Test
    fun isolatedSyntheticDebugQualification() {
        assertEquals(36, Build.VERSION.SDK_INT)
        assertEquals("1", shell("getprop ro.kernel.qemu").trim())
        assertTrue(BuildConfig.DEBUG)
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        assertTrue(BuildConfig.FLAVOR in setOf("pro", "premium"))
        assertEquals("com.securecall.app.${BuildConfig.FLAVOR}.devtest", context.packageName)
        assertFalse(BuildConfig.TESTER_LICENSE_ENABLED)
        assertEquals("", BuildConfig.TESTER_ENTITLEMENT_PUBLIC_KEY)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("sc224PreAppIsolation"))

        val fixture = JSONObject(instrumentation.context.assets.open("sc224-synthetic.json")
            .bufferedReader().use { it.readText() })
        assertTrue(fixture.getBoolean("syntheticDebugOnly"))
        assertTrue("Wrong synthetic verifier", fixture.getString("publicKey") == BuildConfig.ENTITLEMENT_PUBLIC_KEY)
        val now = System.currentTimeMillis() / 1000
        assertTrue(now >= fixture.getLong("issuedAt"))
        assertTrue(fixture.getLong("expiresAt") - now in 120L..3600L)
        val subject = fixture.getString("subject")
        assertTrue(subject.startsWith("sc224-synthetic-"))
        val token = fixture.getJSONObject("tokens").getString(BuildConfig.FLAVOR.uppercase())
        val wrongTierToken = fixture.getJSONObject("tokens").getString(
            if (BuildConfig.FLAVOR == "pro") "PREMIUM" else "PRO")
        val identity = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
        val proof = context.getSharedPreferences("securecall_direct_entitlement", Context.MODE_PRIVATE)
        assertFalse("Existing identity is forbidden", identity.contains("client_id"))
        assertFalse(identity.contains("legacy_client_id"))
        assertFalse(identity.contains("activated_tier"))
        assertTrue("Existing proof is forbidden", proof.all.isEmpty())
        assertTrue(identity.edit().putString("client_id", subject).commit())
        context.registerReceiver(receipt, IntentFilter(RECEIPT), Context.RECEIVER_EXPORTED)
        instrumentation.addMonitor(monitor)
        try {
            rights("FREE")
            checkpoint("empty_denied")
            assertFalse(store.accept("invalid-synthetic-proof"))
            assertFalse("Wrong-tier signed proof accepted", store.accept(wrongTierToken))
            assertTrue(proof.all.isEmpty())
            rights("FREE")
            checkpoint("invalid_denied")
            assertTrue("Real store rejected synthetic proof", store.accept(token))
            rights(BuildConfig.FLAVOR.uppercase())
            assertTrue("Proof bytes changed", proof.getString("token", null) == token)
            checkpoint("paid_store_effective")
            val accepted = snapshot()
            assertFalse(store.accept("invalid-synthetic-replacement"))
            assertFalse(store.accept(wrongTierToken))
            assertTrue("Replacement mutated accepted proof", accepted == snapshot())
            checkpoint("invalid_replacement_denied")
            roundTrip(BuildConfig.FLAVOR.uppercase())
            checkpoint("paid_back_cancel")
            denial(ActivityNotFoundException("Synthetic SC224 missing handler"))
            checkpoint("synthetic_not_found_toast")
            denial(SecurityException("Synthetic SC224 OS denial"))
            checkpoint("synthetic_security_toast")
            assertTrue(store.revoke())
            rights("FREE")
            roundTrip("FREE")
            checkpoint("free_back_cancel")
        } finally {
            instrumentation.removeMonitor(monitor)
            context.unregisterReceiver(receipt)
            assertTrue(store.revoke())
            assertTrue(identity.edit().remove("client_id").commit())
            rights("FREE")
            assertFalse(proof.contains("token"))
            assertFalse(proof.contains("last_verified_time"))
        }
        checkpoint("fixture_cleanup")
    }

    private fun rights(tier: String) {
        assertEquals(tier, store.currentTier())
        assertEquals(tier, TierManager.getCurrentTier(context))
        TierManager.applyTier(context)
        val provider = FeatureProviderRegistry.get()
        assertEquals(tier, provider.tier)
        assertEquals(tier != "FREE", provider.multiDeviceSupport)
        assertEquals(if (tier == "FREE") 10 else 0, provider.maxContacts)
        assertEquals(tier == "PREMIUM", provider.noFallbackMode)
    }

    private fun roundTrip(tier: String) {
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            instrumentation.waitForIdleSync()
            lateinit var original: SettingsActivity
            scenario.onActivity { activity ->
                original = activity
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.settingsContainer) as SettingsFragment
                assertEquals(tier, fragment.findPreference<Preference>("pref_tier")!!.summary.toString())
                fragment.scrollToPreference("pref_check_update")
            }
            instrumentation.waitForIdleSync()
            // Shell visibility is independent of target-package query restrictions; actual dispatch follows below.
            assertEquals(BuildConfig.APPLICATION_ID + ".test/" + Sc224ReceiverActivity::class.java.name,
                shell("cmd package resolve-activity --brief -a android.intent.action.VIEW -d $URL").trim().lineSequence().last())
            for (cancel in listOf(false, true)) {
                rights(tier)
                // Refresh only through the real verifier, before the bounded immutable window.
                if (tier != "FREE") assertTrue(store.accept(requireNotNull(store.tokenForRefresh())))
                val before = snapshot()
                val started = SystemClock.elapsedRealtime()
                attempts.clear()
                delivered.clear()
                onView(withText("Check for Updates")).perform(click())
                await { delivered.size == 1 }
                assertEquals(1, attempts.size)
                val request = attempts.single()
                assertEquals(Intent.ACTION_VIEW, request.action)
                assertEquals(URL, request.dataString)
                assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, request.flags)
                assertNull(request.component)
                assertNull(request.`package`)
                assertNull(request.extras)
                assertNull(request.selector)
                assertNull(request.clipData)
                assertTrue(request.categories.isNullOrEmpty())
                assertEquals(Intent.ACTION_VIEW, delivered.single().action)
                assertEquals(URL, delivered.single().dataString)
                assertEquals(Sc224ReceiverActivity::class.java.name, delivered.single().component?.className)
                syntheticScreenshot("${tier.lowercase()}-${if (cancel) "cancel" else "back"}")
                if (cancel) {
                    val ui = instrumentation.uiAutomation
                    val root = requireNotNull(ui.rootInActiveWindow)
                    val buttons = root.findAccessibilityNodeInfosByText("Cancel synthetic handoff")
                    assertEquals(1, buttons.size)
                    assertTrue(buttons.single().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                } else {
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                }
                await { scenario.state == Lifecycle.State.RESUMED }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertSame(original, activity)
                    assertFalse(activity.isFinishing)
                    val secure = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
                    assertEquals(tier != "FREE", secure) // Never remove FLAG_SECURE for a screenshot.
                    val fragment = activity.supportFragmentManager.findFragmentById(R.id.settingsContainer) as SettingsFragment
                    assertEquals(tier, fragment.findPreference<Preference>("pref_tier")!!.summary.toString())
                    assertTrue(fragment.findPreference<Preference>("pref_check_update")!!.isEnabled)
                }
                assertTrue("Round trip exceeded immutable watermark window", SystemClock.elapsedRealtime() - started < 45_000)
                rights(tier)
                assertTrue("Preference/proof snapshot changed on return", before == snapshot())
                assertEquals(1, attempts.size)
                assertEquals(1, delivered.size)
            }
        }
    }

    private fun denial(failure: RuntimeException) {
        // Synthetic Activity boundary with a real Context and real platform Toast, not OS/OEM denial.
        val activity = object : Activity() {
            var count = 0
            init { attachBaseContext(context) }
            override fun startActivity(intent: Intent) {
                count++
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals(URL, intent.dataString)
                assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
                assertNull(intent.component)
                assertNull(intent.`package`)
                throw failure
            }
        }
        assertTrue(store.accept(requireNotNull(store.tokenForRefresh())))
        val before = snapshot()
        val started = SystemClock.elapsedRealtime()
        val event = instrumentation.uiAutomation.executeAndWaitForEvent({
            instrumentation.runOnMainSync { UpdateManager.checkAndPromptUpdate(activity) }
        }, { event -> event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
            event.text.any { it.toString() == "No browser available" } }, 5_000)
        event.recycle()
        assertEquals(1, activity.count)
        assertTrue(SystemClock.elapsedRealtime() - started < 45_000)
        rights(BuildConfig.FLAVOR.uppercase())
        assertTrue("Synthetic denial mutated preferences/proof", before == snapshot())
    }

    private fun snapshot(): Map<String, Map<String, *>> {
        val names = mutableSetOf("securecall_prefs", "securecall_direct_entitlement", "securecall_subscription", "update_prefs")
        names.add(context.packageName + "_preferences")
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.filter { it.extension == "xml" }
            ?.forEach { names.add(it.nameWithoutExtension) }
        return names.associateWith { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap() }
    }

    private fun syntheticScreenshot(name: String) {
        instrumentation.waitForIdleSync()
        val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
        assertEquals(1, root.findAccessibilityNodeInfosByText("SYNTHETIC SC224 HANDOFF").size)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            assertTrue("Synthetic receiver capture is blank", bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) != Color.BLACK)
            File(context.filesDir, "sc224-$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Timed out waiting for real Android handoff/return", condition())
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command)
        .let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() } }

    private fun checkpoint(name: String) = instrumentation.sendStatus(2, Bundle().apply { putString("sc224_checkpoint", name) })
}

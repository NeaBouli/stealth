package com.securecall.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.securecall.app.data.Contact
import com.securecall.app.ui.adapter.ContactAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measured layout invariants for issue #86 (narrow-phone dialer and bottom navigation).
 * Run on the 320x640dp and tablet emulator profiles; the assertions are size-independent.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenLayoutInstrumentedTest {

    private val dialKeys = intArrayOf(
        R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6,
        R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnStar, R.id.btn0, R.id.btnHash
    )

    @Before
    fun grantRuntimePermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        grant(context, Manifest.permission.RECORD_AUDIO)
        grant(context, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grant(context, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test
    fun dialKeys_areAtLeast48dp_andZeroKeyShowsPlusUnclipped() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                    .selectedItemId = R.id.nav_dialer
                activity.supportFragmentManager.executePendingTransactions()
            }
            onView(withId(R.id.dialPad)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertKeysAtLeast48dp(activity, "dial pad only")
                val zero = activity.findViewById<Button>(R.id.btn0)
                assertEquals("0 key must render the + hint on its own line", 2, zero.layout.lineCount)
                val textRoom = zero.height - zero.totalPaddingTop - zero.totalPaddingBottom
                assertTrue(
                    "0 key text ${zero.layout.height}px exceeds room ${textRoom}px",
                    zero.layout.height <= textRoom
                )
                val fab = activity.findViewById<View>(R.id.fabCall)
                val navHost = activity.findViewById<View>(R.id.nav_host_fragment)
                assertTrue("call button must lie inside the content area", fab.bottomOnScreen() <= navHost.bottomOnScreen())
                // Contact matches take a weighted share while typing; keys must keep 48dp.
                activity.findViewById<View>(R.id.contactSuggestions).visibility = View.VISIBLE
            }
            scenario.onActivity { activity ->
                assertKeysAtLeast48dp(activity, "with contact matches")
            }
        }
    }

    private fun assertKeysAtLeast48dp(activity: MainActivity, state: String) {
        val min = 48 * activity.resources.displayMetrics.density
        for (id in dialKeys) {
            val key = activity.findViewById<Button>(id)
            val name = activity.resources.getResourceEntryName(id)
            assertTrue("$state: $name height ${key.height}px < 48dp", key.height >= min)
            assertTrue("$state: $name width ${key.width}px < 48dp", key.width >= min)
        }
    }

    @Test
    fun bottomNavigation_showsEveryLabelUntruncated_withTouchTargets() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val nav = activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                assertEquals(NavigationBarView.LABEL_VISIBILITY_LABELED, nav.labelVisibilityMode)
                val min = 48 * activity.resources.displayMetrics.density
                for (i in 0 until nav.menu.size()) {
                    val item = nav.findViewById<View>(nav.menu.getItem(i).itemId)
                    val title = nav.menu.getItem(i).title.toString()
                    assertTrue("$title height ${item.height}px < 48dp", item.height >= min)
                    assertTrue("$title width ${item.width}px < 48dp", item.width >= min)
                    val label = item.visibleLabel(title)
                    assertTrue("$title label is not visible", label != null)
                    assertEquals("$title label is ellipsized", 0, label!!.layout.getEllipsisCount(0))
                    assertTrue("$title label wider than its item", label.layout.getLineWidth(0) <= item.width)
                }
            }
        }
    }

    @Test
    fun contactMatch_staysFullyAboveKeyboard() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                    .selectedItemId = R.id.nav_dialer
                activity.supportFragmentManager.executePendingTransactions()
            }
            onView(withId(R.id.btnToggleAlpha)).perform(click())
            scenario.onActivity { activity ->
                val suggestions = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(
                    R.id.contactSuggestions
                )
                suggestions.adapter = ContactAdapter(
                    listOf(Contact(name = "Synthetic Match", phoneOrId = "+490000000001"))
                )
                suggestions.visibility = View.VISIBLE
            }
            onView(withContentDescription("Synthetic Match")).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val row = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(
                    R.id.contactSuggestions
                ).getChildAt(0)
                val visibleFrame = Rect()
                activity.window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
                assertTrue(
                    "contact row bottom ${row.bottomOnScreen()} exceeds IME top ${visibleFrame.bottom}",
                    row.bottomOnScreen() <= visibleFrame.bottom
                )
            }
        }
    }

    private fun View.bottomOnScreen(): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[1] + height
    }

    private fun View.visibleLabel(title: String): TextView? {
        if (this is TextView && text.toString() == title && isShown && layout != null) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                getChildAt(i).visibleLabel(title)?.let { return it }
            }
        }
        return null
    }

    private fun launch(): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsReady = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_complete", true)
            .putBoolean("samsung_battery_shown", true)
            .putLong("battery_opt_last_asked", Long.MAX_VALUE)
            .putString("confirmed_phone_number", "+490000000000")
            .commit()
        assertTrue(prefsReady)
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun grant(context: Context, permission: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
        ParcelFileDescriptor.AutoCloseInputStream(output).use { stream ->
            val buffer = ByteArray(256)
            while (stream.read(buffer) != -1) {
                // Draining the pipe waits for the shell command to finish.
            }
        }
    }
}

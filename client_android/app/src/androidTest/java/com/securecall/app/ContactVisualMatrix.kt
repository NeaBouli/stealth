package com.securecall.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.app.config.TierManager
import com.securecall.app.data.Contact
import com.securecall.app.data.ContactRepository
import com.securecall.app.security.IdentityProtocol
import com.securecall.app.ui.ContactsFragment
import com.securecall.app.ui.adapter.ContactAdapter
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Opt-in, disposable guest only. Never renders an offscreen replacement for a failed window. */
internal object ContactVisualMatrix {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val viewports = listOf(320 to 676, 390 to 844, 820 to 1180, 1180 to 820)
    private val states = listOf("before", "after")
    private const val DIRECTORY = "sc241-contact-visual"
    private const val PNG_LIMIT = 4 * 1024 * 1024
    private const val API_LIMIT = 8 * 1024 * 1024
    private const val METADATA_LIMIT = 128 * 1024

    fun admit(context: Context) {
        val args = InstrumentationRegistry.getArguments()
        assertEquals("Explicit synthetic CI admission is required", "true",
            args.getString("securecallContactVisualGate"))
        assertTrue("Only DEBUG Free builds are admitted", BuildConfig.DEBUG && BuildConfig.FLAVOR == "free")
        assertTrue("Only the ordinary API24/36 matrix is admitted", Build.VERSION.SDK_INT in listOf(24, 36))
        assertTrue("QEMU hardware is required", Build.HARDWARE in listOf("ranchu", "goldfish"))
        assertEquals("Actual QEMU guest property is required", "1", shell("getprop ro.kernel.qemu"))
        assertTrue("Effective FREE is required; never supply paid rights", TierManager.isFreeTier(context))
        for (key in listOf("securecallVisualHead", "securecallVisualTree")) {
            assertTrue("Exact source receipt is required", Regex("[0-9a-f]{40}")
                .matches(args.getString(key, "").orEmpty()))
        }
    }

    fun run(
        context: Context,
        fixtures: List<Contact>,
        positiveId: String,
        positiveSecureId: String,
        closeHost: () -> Unit,
        launchHost: () -> Unit,
        withHost: ((SettingsActivity) -> Unit) -> Unit,
        seed: (List<Contact>) -> Unit,
        partialSnapshot: () -> Unit,
    ) {
        admit(context)
        assertTrue("Visual fixture must start empty", ContactRepository.getAll(context).isEmpty())
        val directory = File(context.filesDir, DIRECTORY)
        assertTrue("Refuse stale or unowned capture directory", !directory.exists())
        val prior = GuestDisplay.read()
        assertTrue("Cannot create owned capture directory", directory.mkdir())
        val synthetic = fixtures.mapIndexed { index, contact ->
            contact.copy(name = when (index) {
                0 -> "A very long synthetic contact name for wrapping and large font inspection"
                1 -> "B synthetic linked contact"
                else -> "C synthetic positive phone"
            })
        } + (0 until 17).map { index ->
            Contact(id = "visual-$index", name = "D synthetic contact %02d".format(index),
                phoneOrId = "android-visual-$index", createdAt = 10L + index)
        }
        val associated = synthetic.map { if (it.id == positiveId) it.copy(secureId = positiveSecureId) else it }
        val captures = JSONArray()
        var violations = 0
        var complete = false
        try {
            for ((width, height) in viewports) for (font in listOf(1, 2)) {
                closeHost()
                shell("settings put system accelerometer_rotation 0")
                shell("settings put system user_rotation 0")
                shell("wm density 160")
                shell("wm size ${width}x$height")
                shell("settings put system font_scale $font.0")
                instrumentation.waitForIdleSync()
                launchHost()
                seed(synthetic)
                withHost { activity ->
                    val fragment = activity.supportFragmentManager
                        .findFragmentById(R.id.settingsContainer) as ContactsFragment
                    ContactsFragment::class.java.getDeclaredMethod("dedupAndRefresh").apply {
                        isAccessible = true
                        invoke(fragment)
                    }
                }
                var adapterBefore: RecyclerView.Adapter<*>? = null
                for (state in states) {
                    if (state == "after") partialSnapshot()
                    val expected = if (state == "before") synthetic else associated
                    assertEquals("Saved identities/metadata must be preserved", expected.associateBy { it.id },
                        ContactRepository.getAll(context).associateBy { it.id })
                    idleList()
                    var recycler: RecyclerView? = null
                    var currentAdapter: RecyclerView.Adapter<*>? = null
                    var shownRows: List<Contact> = emptyList()
                    withHost { activity ->
                        assertWindow(activity, width, height, font)
                        recycler = activity.findViewById(R.id.recyclerContacts)
                        currentAdapter = recycler!!.adapter
                        assertTrue("Production adapter is required", currentAdapter is ContactAdapter)
                        val field = ContactAdapter::class.java.getDeclaredField("contacts").apply { isAccessible = true }
                        @Suppress("UNCHECKED_CAST")
                        val displayed = field.get(currentAdapter) as List<Contact>
                        shownRows = displayed
                        assertEquals(expected.associateBy { it.id }, displayed.associateBy { it.id })
                        if (state == "after") assertEquals(positiveId, displayed.first().id)
                    }
                    if (state == "before") adapterBefore = currentAdapter
                    assertTrue("Partial snapshot must retain adapter identity", currentAdapter === adapterBefore)
                    val list = checkNotNull(recycler)
                    val measured = Measurement()
                    onView(withId(R.id.recyclerContacts)).perform(swipeUp())
                    idleList()
                    var touchScroll = false
                    withHost { touchScroll = list.computeVerticalScrollOffset() > 0 }
                    measured.check(touchScroll, "touch_scroll")
                    for (position in expected.indices) {
                        withHost { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0) }
                        idleList()
                        withHost { activity ->
                            val row = list.findViewHolderForAdapterPosition(position)?.itemView
                            measured.check(row != null, "missing_row")
                            if (row != null) {
                                measured.rows++
                                val expectedRow = shownRows[position]
                                measured.check(row.contentDescription == expectedRow.name, "row_identity")
                                measured.check(row.findViewById<TextView>(R.id.txtName).text
                                    .startsWith(expectedRow.name), "name_binding")
                                val subtitle = if (expectedRow.secureId != null &&
                                    !IdentityProtocol.isDirectClientId(expectedRow.phoneOrId)) {
                                    "${expectedRow.phoneOrId} | ${expectedRow.secureId}"
                                } else expectedRow.phoneOrId
                                measured.check(row.findViewById<TextView>(R.id.txtPhoneOrId).text.toString() ==
                                    subtitle, "identity_binding")
                                measureRow(row, list, activity.findViewById(R.id.fabAddContact), measured, true)
                            }
                        }
                    }
                    var reachedEnd = false
                    withHost { reachedEnd = !list.canScrollVertically(1) }
                    measured.check(reachedEnd, "scroll_end")
                    withHost { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0) }
                    idleList()
                    withHost { activity ->
                        val frame = bounds(activity.findViewById(R.id.settingsContainer))
                        val search = activity.findViewById<View>(R.id.searchInput)
                        val add = activity.findViewById<View>(R.id.fabAddContact)
                        for (control in listOf(search, add)) {
                            measured.check(frame.contains(bounds(control)), "control_bounds")
                            measured.target(control)
                        }
                        for (index in 0 until list.childCount) {
                            measureRow(list.getChildAt(index), list, add, measured, false)
                        }
                        assertWindow(activity, width, height, font)
                    }
                    val name = "sc241-${width}x$height-f$font-$state.png"
                    val colors = capture(directory, name, width, height)
                    violations += measured.violations
                    captures.put(JSONObject().apply {
                        put("name", name); put("width", width); put("height", height)
                        put("fontScale", font); put("state", state); put("densityDpi", 160)
                        put("rowCount", expected.size); put("rowsChecked", measured.rows)
                        put("minTargetPx", measured.minTarget); put("minRowHeightPx", measured.minRowHeight)
                        put("maxTextLines", measured.maxLines); put("violations", measured.violations)
                        put("errors", JSONObject(measured.errors))
                        put("scrollReachedEnd", reachedEnd); put("adapterSame", true)
                        put("touchScroll", touchScroll)
                        put("savedDataPreserved", true); put("windowFocused", true); put("pixelColors", colors)
                    })
                    assertTrue("Per-API capture budget exceeded; never downscale/drop states",
                        directory.listFiles()!!.sumOf { it.length() } <= API_LIMIT - METADATA_LIMIT)
                }
            }
            complete = true
        } finally {
            try {
                closeHost()
            } finally {
                try {
                    prior.restore()
                } finally {
                    if (!complete) {
                        expectedNames().forEach { File(directory, it).delete() }
                        directory.delete()
                    }
                }
            }
        }
        val args = InstrumentationRegistry.getArguments()
        val metadata = JSONObject().apply {
            put("schema", 1); put("captureMode", "uiAutomation-window")
            put("head", args.getString("securecallVisualHead")); put("tree", args.getString("securecallVisualTree"))
            put("api", Build.VERSION.SDK_INT); put("status", if (violations == 0) "passed" else "failed")
            put("restored", true); put("violations", violations); put("captures", captures)
        }.toString().toByteArray(Charsets.UTF_8)
        assertTrue("Aggregate metadata exceeds fixed bound", metadata.size <= METADATA_LIMIT)
        File(directory, "matrix.json").writeBytes(metadata)
        assertEquals("Exactly sixteen actual-window captures are required", 16, captures.length())
        assertTrue("Per-API export cap exceeded", directory.listFiles()!!.sumOf { it.length() } <= API_LIMIT)
        // Keep all sixteen images on a layout failure; collector must export them only as FAILED evidence.
        assertEquals("Synthetic contact visual layout violations (see bounded aggregate)", 0, violations)
    }

    private fun assertWindow(activity: SettingsActivity, width: Int, height: Int, font: Int) {
        assertTrue("Refuse FLAG_SECURE; never clear it", activity.window.attributes.flags and
            WindowManager.LayoutParams.FLAG_SECURE == 0)
        assertTrue("Actual owned window must be focused", activity.hasWindowFocus())
        assertTrue("Effective FREE must remain unchanged", TierManager.isFreeTier(activity))
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealMetrics(metrics)
        assertEquals(width, metrics.widthPixels); assertEquals(height, metrics.heightPixels)
        assertEquals(160, metrics.densityDpi)
        assertTrue("Font configuration did not apply", abs(activity.resources.configuration.fontScale - font) < 0.01f)
        assertEquals(width, activity.window.decorView.width); assertEquals(height, activity.window.decorView.height)
    }

    private class Measurement {
        var violations = 0
        var rows = 0
        var minTarget = Int.MAX_VALUE
        var minRowHeight = Int.MAX_VALUE
        var maxLines = 0
        val errors = mutableMapOf<String, Int>()
        fun check(condition: Boolean, code: String) {
            if (!condition) {
                violations++
                errors[code] = (errors[code] ?: 0) + 1
            }
        }
        fun target(view: View) {
            minTarget = minOf(minTarget, view.width, view.height)
            check(view.width >= 48 && view.height >= 48, "target_min")
        }
    }

    private fun measureRow(row: View, list: RecyclerView, add: View, result: Measurement, requireFull: Boolean) {
        val rowRect = bounds(row)
        val listRect = bounds(list)
        result.minRowHeight = minOf(result.minRowHeight, row.height)
        result.check(rowRect.left >= listRect.left && rowRect.right <= listRect.right, "row_horizontal")
        result.check(row.height <= list.height - list.paddingTop - list.paddingBottom, "row_height")
        val full = listRect.contains(rowRect)
        if (requireFull) result.check(full, "row_not_fully_reachable")
        if (!full) return // Ordinary vertical clipping at a scroll viewport is not text-layout clipping.
        result.check(row.height >= 48 && row.isClickable, "row_target")
        val call = row.findViewById<View>(R.id.btnCallContact)
        result.target(call)
        result.check(rowRect.contains(bounds(call)), "call_bounds")
        result.check(!Rect.intersects(bounds(call), bounds(add)), "floating_control_overlap")
        fun text(view: View) {
            if (view.visibility != View.VISIBLE) return
            result.check(rowRect.contains(bounds(view)), "child_bounds")
            if (view is TextView && view.text.isNotEmpty()) {
                val layout = view.layout
                result.check(layout != null, "text_layout")
                if (layout != null) {
                    result.maxLines = maxOf(result.maxLines, layout.lineCount)
                    result.check(layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom,
                        "text_height")
                    for (line in 0 until layout.lineCount) {
                        result.check(layout.getEllipsisCount(line) == 0, "text_ellipsis")
                        result.check(layout.getLineRight(line) - layout.getLineLeft(line) <=
                            view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1, "text_width")
                    }
                }
                result.check(!Rect.intersects(bounds(view), bounds(call)), "text_call_overlap")
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) text(view.getChildAt(index))
        }
        text(row)
    }

    private fun bounds(view: View): Rect {
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        return Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }

    private fun idleList() {
        onView(withId(R.id.recyclerContacts)).check(matches(isDisplayed()))
        instrumentation.waitForIdleSync()
    }

    private fun capture(directory: File, name: String, width: Int, height: Int): Int {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Actual-window capture unavailable" }
        try {
            assertEquals(width, bitmap.width); assertEquals(height, bitmap.height)
            val colors = mutableSetOf<Int>()
            for (y in 0 until height step 13) for (x in 0 until width step 13) colors.add(bitmap.getPixel(x, y))
            assertTrue("Actual window is blank", colors.size >= 8)
            val bytes = ByteArrayOutputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
            assertTrue("PNG exceeds cap; never crop/resize/drop a state", bytes.size <= PNG_LIMIT)
            val file = File(directory, name)
            assertTrue("Refuse duplicate capture", file.createNewFile())
            file.writeBytes(bytes)
            return colors.size
        } finally {
            bitmap.recycle()
        }
    }

    private fun expectedNames() = viewports.flatMap { (width, height) ->
        listOf(1, 2).flatMap { font -> states.map { "sc241-${width}x$height-f$font-$it.png" } }
    }

    private data class GuestDisplay(
        val size: String, val density: String, val font: String, val autoRotation: String, val userRotation: String,
    ) {
        fun restore() {
            var failure: Throwable? = null
            val actions = listOf<() -> Unit>(
                { shell("wm size ${overrideValue(size, "size") ?: "reset"}") },
                { shell("wm density ${overrideValue(density, "density") ?: "reset"}") },
                { restoreSetting("font_scale", font) },
                { restoreSetting("user_rotation", userRotation) },
                { restoreSetting("accelerometer_rotation", autoRotation) },
            )
            for (action in actions) try { action() } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
            instrumentation.waitForIdleSync()
            if (failure != null) throw failure!!
            assertEquals("Guest overrides must restore exactly", this, read())
        }

        companion object {
            fun read(): GuestDisplay {
                val size = shell("wm size")
                val density = shell("wm density")
                assertTrue("Unsupported wm size response", Regex("Physical size: [0-9]+x[0-9]+(?:\\nOverride size: [0-9]+x[0-9]+)?").matches(size))
                assertTrue("Unsupported wm density response", Regex("Physical density: [0-9]+(?:\\nOverride density: [0-9]+)?").matches(density))
                return GuestDisplay(size, density, setting("font_scale"), setting("accelerometer_rotation"),
                    setting("user_rotation"))
            }

            private fun overrideValue(value: String, kind: String): String? =
                value.lineSequence().firstOrNull { it.startsWith("Override $kind: ") }?.substringAfter(": ")

            private fun setting(key: String): String = shell("settings get system $key").also {
                assertTrue("Unexpected guest setting", it == "null" || Regex("[0-9]+(?:\\.[0-9]+)?").matches(it))
            }

            private fun restoreSetting(key: String, value: String) {
                if (value == "null") shell("settings delete system $key") else shell("settings put system $key $value")
            }
        }
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(512)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                bytes.write(buffer, 0, count)
                assertTrue("Bounded guest command output exceeded", bytes.size() <= 4096)
            }
            bytes.toString("UTF-8").trim()
        }
    }
}

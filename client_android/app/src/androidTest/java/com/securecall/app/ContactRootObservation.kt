package com.securecall.app

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Root
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.app.config.TierManager
import java.util.ArrayDeque
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

/** Candidates are observations, never evidence of selection or ownership. */
internal class ContactRootObservation private constructor(private val head: String, private val tree: String) {
    private var reported = false

    fun acquire(title: String, scenario: ActivityScenario<SettingsActivity>, action: (Matcher<Root>) -> Unit) {
        val delegate = isDialog()
        if (reported) { action(delegate); return }
        val candidates = mutableListOf<Root>()
        var incomplete = false
        val matcher = object : BaseMatcher<Root>() {
            override fun matches(item: Any?): Boolean {
                val result = delegate.matches(item)
                try {
                    if (result && item is Root && candidates.none { it.decorView === item.decorView }) {
                        if (candidates.size < 8) candidates.add(item) else incomplete = true
                    }
                } catch (_: Throwable) { incomplete = true }
                return result
            }
            override fun describeTo(description: Description) = delegate.describeTo(description)
            override fun describeMismatch(item: Any?, description: Description) =
                delegate.describeMismatch(item, description)
        }
        try {
            action(matcher)
        } catch (original: Throwable) {
            reported = true
            try { report(title, scenario, candidates, incomplete) } catch (_: Throwable) {
                // Observation and transport failures must never mask the original failure.
            }
            throw original
        }
    }

    private fun report(
        title: String, scenario: ActivityScenario<SettingsActivity>, roots: List<Root>, incomplete: Boolean,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val payload = Bundle()
        fun field(key: String, value: String) = payload.putString("sc241_root_$key", value)
        field("head", head)
        field("tree", tree)
        field("phase", "MENU")
        field("selected", "UNKNOWN")
        field("candidate_count", roots.size.toString())
        field("candidates_incomplete", incomplete.toString())
        for (key in listOf("host_started", "host_resumed", "host_destroyed", "host_finishing", "host_focus",
            "interactive", "keyguard_locked", "device_locked")) field(key, "UNKNOWN")
        for (index in 0 until 8) {
            field("candidate_${index}_type", "-1")
            for (key in listOf("attached", "focus", "synthetic_title", "host_context")) {
                field("candidate_${index}_$key", "UNKNOWN")
            }
        }
        var host: SettingsActivity? = null
        try {
            scenario.onActivity { activity ->
                try {
                    host = activity
                    field("host_started", tri { activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) })
                    field("host_resumed", tri { activity.lifecycle.currentState == Lifecycle.State.RESUMED })
                    field("host_destroyed", tri { activity.isDestroyed })
                    field("host_finishing", tri { activity.isFinishing })
                    field("host_focus", tri { activity.hasWindowFocus() })
                } catch (_: Throwable) { /* Keep callback failures off the UI thread. */ }
            }
        } catch (_: Throwable) { /* Host unavailable: keep UNKNOWN. */ }
        try {
            instrumentation.runOnMainSync {
                try {
                    roots.forEachIndexed { index, root ->
                        val prefix = "candidate_${index}_"
                        val type = try { if (root.windowLayoutParams.isPresent) root.windowLayoutParams.get().type else -1 }
                            catch (_: Throwable) { -1 }
                        field(prefix + "type", if (type in 1..2999) type.toString() else "-1")
                        field(prefix + "attached", tri { root.decorView.isAttachedToWindow })
                        field(prefix + "focus", tri { root.decorView.hasWindowFocus() })
                        field(prefix + "synthetic_title", tri { titleMatches(root.decorView, title) })
                        field(prefix + "host_context", tri { host?.let { hostMatches(root.decorView.context, it) } })
                    }
                    val context = instrumentation.targetContext
                    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    field("interactive", tri { power?.isInteractive })
                    field("keyguard_locked", tri { keyguard?.isKeyguardLocked })
                    field("device_locked", tri { keyguard?.isDeviceLocked })
                } catch (_: Throwable) { /* Partial snapshot stays explicitly unavailable. */ }
            }
        } catch (_: Throwable) { /* Unavailable observations remain UNKNOWN/-1. */ }
        instrumentation.sendStatus(2, payload)
    }

    private fun titleMatches(root: View, title: String): Boolean? {
        val pending = ArrayDeque<View>()
        pending.add(root)
        repeat(128) {
            if (pending.isEmpty()) return false
            val view = pending.removeFirst()
            if (view is TextView && TextUtils.equals(view.text, title)) return true
            if (view is ViewGroup) {
                if (view.childCount > 128 - pending.size) return null
                for (index in 0 until view.childCount) pending.addLast(view.getChildAt(index))
            }
        }
        return if (pending.isEmpty()) false else null
    }

    private fun hostMatches(context: Context, host: SettingsActivity): Boolean? {
        var current = context
        repeat(8) {
            if (current === host) return true
            if (current !is ContextWrapper) return false
            current = (current as ContextWrapper).baseContext ?: return null
        }
        return null
    }

    private fun tri(read: () -> Boolean?): String = try {
        when (read()) { true -> "true"; false -> "false"; null -> "UNKNOWN" }
    } catch (_: Throwable) { "UNKNOWN" }

    companion object {
        fun admit(context: Context): ContactRootObservation? {
            val args = InstrumentationRegistry.getArguments()
            if (args.getString("securecallContactRootProbe") != "true") return null
            check(args.getString("securecallRootProbeHosted") == "true")
            check(args.getString("securecallContactVisualGate") != "true" && args.getString("securecallLivePinTest") != "true")
            check(BuildConfig.DEBUG && BuildConfig.FLAVOR == "free" && Build.VERSION.SDK_INT == 24)
            check(Build.HARDWARE in listOf("ranchu", "goldfish") && TierManager.isFreeTier(context))
            val head = args.getString("securecallRootProbeHead", "").orEmpty()
            val tree = args.getString("securecallRootProbeTree", "").orEmpty()
            check(listOf(head, tree).all { Regex("[0-9a-f]{40}").matches(it) })
            val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu")
            val qemu = ParcelFileDescriptor.AutoCloseInputStream(fd).use {
                val bytes = ByteArray(16)
                String(bytes, 0, maxOf(0, it.read(bytes)), Charsets.US_ASCII).trim()
            }
            check(qemu == "1")
            return ContactRootObservation(head, tree)
        }
    }
}

package com.securecall.app.security

import java.io.File
import java.io.IOException

/**
 * The local wipe contract shared by every STEALTH-DELETE entry point.
 * Deregistration only queues a best-effort send on the existing connection; never wait for
 * a connection or acknowledgement. Attempt every local step even if an earlier step fails.
 * The caller restarts the process only after all steps have been attempted.
 */
internal object StealthDeleteContract {
    enum class Step {
        DEREGISTER,
        DISCONNECT,
        IDENTITY_SIGNING_KEY,
        SHARED_PREFERENCES,
        DATABASES,
        FILES,
        CACHE,
        EXTERNAL_CACHE,
        NOTIFICATIONS,
        CLIPBOARD,
    }

    fun run(perform: (Step) -> Unit): List<Step> = Step.entries.filter { step ->
        try {
            perform(step)
            false
        } catch (_: Exception) {
            true
        }
    }

    fun preferenceNames(packageName: String, filenames: List<String>): Set<String> =
        linkedSetOf(
            "securecall_contacts",
            "securecall_call_history",
            "securecall_prefs",
            "${packageName}_preferences",
        ).apply {
            // A crash may leave only the Android SharedPreferences backup (.xml.bak).
            filenames.forEach { filename ->
                val xmlName = filename.removeSuffix(".bak")
                if (xmlName.endsWith(".xml") && xmlName.length > 4) {
                    add(xmlName.removeSuffix(".xml"))
                }
            }
        }

    fun <T> attemptAll(items: Iterable<T>, action: (T) -> Unit) {
        var failed = false
        items.forEach { item ->
            try {
                action(item)
            } catch (_: Exception) {
                failed = true
            }
        }
        check(!failed) { "Some wipe artifacts could not be removed" }
    }

    /** Keep the storage root available, but remove every nested artifact. */
    fun deleteContents(directory: File) {
        if (!directory.exists()) return
        val children = directory.listFiles() ?: throw IOException("Cannot enumerate wipe directory")
        attemptAll(children.asList()) { child ->
            check(child.deleteRecursively()) { "Cannot remove wipe artifact" }
        }
    }
}

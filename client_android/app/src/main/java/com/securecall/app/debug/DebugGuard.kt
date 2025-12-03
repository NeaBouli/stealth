package com.securecall.app.debug

/**
 * CRYPTO-09: Guard to prevent log spam.
 *
 * Allows printing only:
 * - every X milliseconds
 * - or when value actually changes
 */
object DebugGuard {

    private var lastNoncePrinted: Long = -1L
    private var lastPrintTime: Long = 0L

    private const val MIN_INTERVAL_MS = 300L

    fun allowNoncePrint(current: Long): Boolean {
        val now = System.currentTimeMillis()

        // changed value?
        if (current != lastNoncePrinted) {
            lastNoncePrinted = current
            lastPrintTime = now
            return true
        }

        // interval exceeded?
        if (now - lastPrintTime > MIN_INTERVAL_MS) {
            lastPrintTime = now
            return true
        }

        // block
        return false
    }
}

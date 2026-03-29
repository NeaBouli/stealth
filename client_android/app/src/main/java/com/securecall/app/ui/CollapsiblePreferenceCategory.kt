package com.securecall.app.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

/**
 * Custom PreferenceCategory that supports both expand AND collapse.
 *
 * Respects programmatically hidden preferences: tracks visibility state
 * before collapsing so expand only restores prefs that were visible.
 */
class CollapsiblePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PreferenceCategory(context, attrs) {

    private var isExpanded = false
    private var originalTitle: CharSequence? = null
    private var attached = false
    // Keys of preferences that were visible before last collapse
    private var visibleBeforeCollapse: Set<String>? = null

    init {
        isSelectable = false
    }

    override fun onAttached() {
        super.onAttached()
        if (!attached) {
            originalTitle = title
            attached = true
        }
        // Initial state: collapsed. Don't snapshot yet (SettingsFragment hasn't configured visibility)
        // Use a small delay to let SettingsFragment finish setting up
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (!isExpanded) {
                // Snapshot current visibility (set by SettingsFragment) then collapse
                snapshotVisible()
                hideAll()
                updateTitle()
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) expandChildren() else collapseChildren()
            updateTitle()
        }
    }

    private fun snapshotVisible() {
        val visible = mutableSetOf<String>()
        for (i in 0 until preferenceCount) {
            val pref = getPreference(i)
            if (pref.isVisible && pref.key != null) {
                visible.add(pref.key)
            }
        }
        visibleBeforeCollapse = visible
    }

    private fun hideAll() {
        for (i in 0 until preferenceCount) {
            getPreference(i).isVisible = false
        }
    }

    private fun collapseChildren() {
        // Save which were visible before collapsing
        snapshotVisible()
        hideAll()
    }

    private fun expandChildren() {
        val snapshot = visibleBeforeCollapse
        for (i in 0 until preferenceCount) {
            val pref = getPreference(i)
            // Restore only prefs that were visible before last collapse
            // If no snapshot exists (first expand), show all
            pref.isVisible = snapshot == null || pref.key == null || pref.key in snapshot
        }
    }

    private fun updateTitle() {
        val base = originalTitle ?: return
        title = if (isExpanded) "\u25BC  $base" else "\u25B6  $base"
    }
}

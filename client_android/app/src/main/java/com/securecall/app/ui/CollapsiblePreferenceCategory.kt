package com.securecall.app.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

/**
 * Custom PreferenceCategory that supports both expand AND collapse.
 * Standard Android PreferenceCategory with initialExpandedChildrenCount only supports expand.
 *
 * All children start hidden (collapsed). Tapping the category header toggles visibility.
 * Arrow indicator: ▶ collapsed, ▼ expanded.
 */
class CollapsiblePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PreferenceCategory(context, attrs) {

    private var isExpanded = false
    private var originalTitle: CharSequence? = null
    private var attached = false

    init {
        // PreferenceCategory is not selectable by default — we need clicks
        isSelectable = false
    }

    override fun onAttached() {
        super.onAttached()
        if (!attached) {
            originalTitle = title
            attached = true
        }
        // Collapse all children on attach
        collapseChildren()
        updateTitle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // Force the entire header row to be clickable
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) expandChildren() else collapseChildren()
            updateTitle()
        }
    }

    private fun collapseChildren() {
        for (i in 0 until preferenceCount) {
            getPreference(i).isVisible = false
        }
    }

    private fun expandChildren() {
        for (i in 0 until preferenceCount) {
            getPreference(i).isVisible = true
        }
    }

    private fun updateTitle() {
        val base = originalTitle ?: return
        title = if (isExpanded) "\u25BC  $base" else "\u25B6  $base"
    }
}

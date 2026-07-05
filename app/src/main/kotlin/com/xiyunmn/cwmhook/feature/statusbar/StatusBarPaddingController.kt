package com.xiyunmn.cwmhook.feature.statusbar

import android.view.View
import java.util.WeakHashMap

internal class StatusBarPaddingController {
    private val paddingStates = WeakHashMap<View, PaddingState>()

    fun applyTopPadding(view: View, topInset: Int) {
        val state = synchronized(paddingStates) {
            val existing = paddingStates[view]
            if (existing == null || existing.wasChangedOutside(view)) {
                PaddingState.from(view).also { paddingStates[view] = it }
            } else {
                existing
            }
        }
        val targetTop = state.top + topInset
        if (
            view.paddingLeft != state.left ||
            view.paddingTop != targetTop ||
            view.paddingRight != state.right ||
            view.paddingBottom != state.bottom
        ) {
            view.setPadding(state.left, targetTop, state.right, state.bottom)
        }
        state.appliedTop = targetTop
    }

    fun restoreTopPadding(view: View) {
        val state = synchronized(paddingStates) {
            paddingStates.remove(view)
        } ?: return
        if (
            view.paddingLeft != state.left ||
            view.paddingTop != state.top ||
            view.paddingRight != state.right ||
            view.paddingBottom != state.bottom
        ) {
            view.setPadding(state.left, state.top, state.right, state.bottom)
        }
    }
}

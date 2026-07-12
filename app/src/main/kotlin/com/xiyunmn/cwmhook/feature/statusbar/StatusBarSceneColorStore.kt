package com.xiyunmn.cwmhook.feature.statusbar

import android.content.Context

internal class StatusBarSceneColorStore(
    private val colorCacheProvider: (Context) -> StatusBarColorCache,
) {
    fun remember(context: Context, state: StatusBarWindowState, color: Int) {
        val changed = state.cachedColor != color || state.colorDirty
        state.cachedColor = color
        state.colorDirty = false
        state.rememberActiveColor(color)
        // Resolved colors describe the current rendered host View. Persisting
        // them across content, scroll and skin generations is the source of
        // stale cross-page colors, so v2 keeps them process-local only.
    }
}

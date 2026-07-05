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
        if (changed) {
            colorCacheProvider(context).put(state.activeSkinKey, state.activeSceneKey, color)
        }
    }
}

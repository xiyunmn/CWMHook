package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Color
import android.view.View
import android.view.Window

internal class StatusBarWindowIntegrityChecker(
    private val sceneRules: StatusBarSceneRules,
    private val scrimController: StatusBarScrimController,
    private val windowController: StatusBarWindowController,
    private val readerMenuVisible: (View) -> Boolean,
) {
    fun isIntact(window: Window, decorView: View, state: StatusBarWindowState): Boolean {
        if (sceneRules.isReaderScene(state.activeSceneKey) && !readerMenuVisible(decorView)) {
            return true
        }
        val expectedColor = state.cachedColor ?: return false
        if (window.statusBarColor != expectedColor) return false
        if (decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN != 0) return false
        val expectsLightStatusBar = Color.luminance(expectedColor) > 0.55
        val hasLightStatusBar = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
        return expectsLightStatusBar == hasLightStatusBar
    }
}

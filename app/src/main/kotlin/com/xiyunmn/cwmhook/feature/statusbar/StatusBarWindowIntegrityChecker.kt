package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
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
        if (window.statusBarColor != Color.TRANSPARENT) {
            return false
        }
        val requiredFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (decorView.systemUiVisibility and requiredFlags != requiredFlags) {
            return false
        }
        val expectedColor = state.cachedColor ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val expectsLightStatusBar = Color.luminance(expectedColor) > 0.55
            val hasLightStatusBar = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
            if (expectsLightStatusBar != hasLightStatusBar) {
                return false
            }
        }
        val topInset = if (windowController.isFullscreen(window, decorView)) 0 else windowController.statusBarHeight(decorView)
        if (topInset <= 0) {
            return true
        }
        val contentRoot = window.findViewById<ViewGroup>(android.R.id.content) ?: return false
        val scrim = scrimController.find(contentRoot) ?: return false
        return scrim.visibility == View.VISIBLE && scrimController.solidBackgroundColor(scrim) == expectedColor
    }
}

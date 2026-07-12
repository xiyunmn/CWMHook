package com.xiyunmn.cwmhook.feature.statusbar

import android.content.Context
import android.view.View
import android.view.Window

internal class StatusBarApplyPolicy(
    private val sceneRules: StatusBarSceneRules,
    private val enabledProvider: (Context) -> Boolean,
    private val readerMenuVisible: (View) -> Boolean,
    private val transientOverlayVisible: (Window) -> Boolean,
    private val registeredMainWindow: (Window) -> Boolean,
) {
    fun applyBlockReason(window: Window): String? {
        val decorView = window.decorView
        if (!isEnabled(decorView.context)) {
            return "disabled"
        }
        if (!registeredMainWindow(window)) {
            return "notActivityMainWindow"
        }
        if (isTransientOverlayVisible(window)) {
            return "transientOverlay"
        }
        return null
    }

    fun canSample(window: Window): Boolean {
        return applyBlockReason(window) == null
    }

    fun shouldManageWindow(window: Window): Boolean {
        return isEnabled(window.decorView.context) && registeredMainWindow(window)
    }

    fun isReaderBypass(sceneKey: String, decorView: View): Boolean {
        return sceneRules.isReaderScene(sceneKey) && !readerMenuVisible(decorView)
    }

    private fun isEnabled(context: Context): Boolean {
        return runCatching { enabledProvider(context) }.getOrDefault(true)
    }

    private fun isTransientOverlayVisible(window: Window): Boolean {
        return runCatching { transientOverlayVisible(window) }.getOrDefault(false)
    }
}

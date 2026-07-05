package com.xiyunmn.cwmhook.feature.statusbar

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

internal class StatusBarReaderBypassRestorer(
    private val paddingController: StatusBarPaddingController,
    private val scrimController: StatusBarScrimController,
    private val logTag: String,
) {
    fun restore(
        window: Window,
        decorView: View,
        state: StatusBarWindowState,
        reason: String,
        sceneKey: String,
        skinKey: String,
    ) {
        val contentRoot = window.findViewById<ViewGroup>(android.R.id.content)
        val appRoot = contentRoot?.let { scrimController.findAppRoot(it) }
        if (contentRoot != null) {
            scrimController.hide(contentRoot)
        }
        if (appRoot != null) {
            paddingController.restoreTopPadding(appRoot)
        }
        state.pendingSample = false
        state.readerOriginalWindowState?.restore(window, decorView)
        state.readerOriginalWindowState = null
        state.cachedColor = null
        state.colorDirty = true
        ModuleFileLogger.throttled(
            key = "reader-bypass:${System.identityHashCode(window)}:$reason",
            intervalMs = 800L,
            priority = Log.DEBUG,
            tag = logTag,
            message = "reader bypass reason=$reason skin=$skinKey scene=$sceneKey",
        )
    }
}

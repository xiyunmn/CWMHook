package com.xiyunmn.cwmhook.feature.statusbar

import android.view.View
import android.view.ViewGroup
import android.view.Window

internal class StatusBarContentInsetApplier(
    private val colorCoordinator: StatusBarColorCoordinator,
    private val paddingController: StatusBarPaddingController,
    private val scrimController: StatusBarScrimController,
    private val windowController: StatusBarWindowController,
) {
    fun apply(
        window: Window,
        decorView: View,
        topInset: Int,
        surfaceColor: Int?,
        state: StatusBarWindowState,
        forceSample: Boolean,
    ) {
        val contentRoot = window.findViewById<ViewGroup>(android.R.id.content) ?: return
        val appRoot = scrimController.findAppRoot(contentRoot)
        if (appRoot == null) {
            scrimController.hide(contentRoot)
            return
        }

        // The host already lays its content below the status bar. Adding an
        // inset here moves every page down and corrupts scroll/transition
        // geometry. The scrim is an overlay only; always undo legacy padding
        // from an earlier module process before resolving color.
        paddingController.restoreTopPadding(appRoot)
        if (topInset > 0 && surfaceColor != null) {
            val resolvedColor = colorCoordinator.resolveTopSurfaceColor(appRoot, topInset, state, surfaceColor)
            scrimController.hide(contentRoot)
            windowController.applySystemBarAppearance(window, decorView, resolvedColor)
            colorCoordinator.scheduleRenderedSample(window, contentRoot, appRoot, topInset, state, forceSample)
        } else {
            scrimController.hide(contentRoot)
        }
    }
}

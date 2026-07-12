package com.xiyunmn.cwmhook.feature.statusbar

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.Window
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import java.util.Locale

internal class StatusBarRuntimeApplier(
    private val sceneRules: StatusBarSceneRules,
    private val colorResolver: StatusBarColorResolver,
    private val colorCoordinator: StatusBarColorCoordinator,
    private val paddingController: StatusBarPaddingController,
    private val scrimController: StatusBarScrimController,
    private val windowController: StatusBarWindowController,
    private val windowRegistry: StatusBarWindowRegistry,
    private val sceneResolver: StatusBarSceneResolver,
    private val colorCacheProvider: (Context) -> StatusBarColorCache,
    private val applyPolicy: StatusBarApplyPolicy,
    private val readerMenuVisible: (View) -> Boolean,
    private val applying: ThreadLocal<Boolean>,
    private val logTag: String,
) {
    private val integrityChecker = StatusBarWindowIntegrityChecker(
        sceneRules = sceneRules,
        scrimController = scrimController,
        windowController = windowController,
        readerMenuVisible = readerMenuVisible,
    )
    private val contentInsetApplier = StatusBarContentInsetApplier(
        colorCoordinator = colorCoordinator,
        paddingController = paddingController,
        scrimController = scrimController,
        windowController = windowController,
    )

    fun apply(
        window: Window,
        reason: String,
        forceSample: Boolean = false,
        sceneKeyOverride: String? = null,
    ) {
        if (applying.get()) {
            return
        }
        applying.set(true)
        try {
            val decorView = window.decorView
            if (isApplyBlocked(window, reason)) {
                return
            }
            val state = windowRegistry.state(window)
            val sceneKey = sceneKeyOverride ?: sceneResolver.resolveWindowSceneKey(window, decorView, state)
            val readerScene = sceneRules.isReaderScene(sceneKey)
            if (readerScene) {
                return
            }
            val skinKey = colorResolver.currentSkinKey(decorView.context)
            val persistedColor: Int? = null
            val restored = state.activate(sceneKey, skinKey, persistedColor)
            if (forceSample) {
                state.markDirty(clearCached = true)
            }

            windowController.configureTransparentStatusBar(window, decorView)

            val topInset = if (windowController.isFullscreen(window, decorView)) 0 else windowController.statusBarHeight(decorView)
            val surfaceColor = if (topInset > 0) {
                colorResolver.directSceneColor(decorView.context, skinKey, sceneKey)
                    ?: state.cachedColor
                    ?: window.statusBarColor.takeIf { Color.alpha(it) == 255 }
                    ?: colorResolver.fallbackColor(decorView.context, skinKey)
            } else {
                null
            }

            windowController.applySystemBarAppearance(window, decorView, surfaceColor)
            contentInsetApplier.apply(window, decorView, topInset, surfaceColor, state, forceSample)

            ModuleFileLogger.throttled(
                key = "apply:${System.identityHashCode(window)}:$reason",
                intervalMs = 800L,
                priority = Log.DEBUG,
                tag = logTag,
                message = "apply reason=$reason skin=$skinKey scene=$sceneKey topInset=$topInset " +
                    "color=${formatColor(surfaceColor)} restored=$restored dirty=${state.colorDirty}",
            )
        } catch (throwable: Throwable) {
            Log.e(logTag, "Failed to apply immersive status bar: $reason", throwable)
            ModuleFileLogger.e(logTag, "Failed to apply immersive status bar: $reason", throwable)
        } finally {
            applying.set(false)
        }
    }

    fun applyIfNeeded(window: Window, reason: String) {
        if (applying.get()) {
            return
        }
        val decorView = window.decorView
        if (isApplyBlocked(window, reason)) {
            return
        }
        val state = windowRegistry.state(window)
        val sceneKey = sceneResolver.resolveWindowSceneKey(window, decorView, state)
        val skinKey = colorResolver.currentSkinKey(decorView.context)
        if (
            state.isCleanFor(sceneKey, skinKey) &&
            integrityChecker.isIntact(window, decorView, state)
        ) {
            return
        }
        apply(window, reason, sceneKeyOverride = sceneKey)
    }

    fun shouldManageWindow(window: Window): Boolean {
        return runCatching {
            val decorView = window.decorView
            if (!applyPolicy.shouldManageWindow(window)) {
                return@runCatching false
            }
            val state = windowRegistry.state(window)
            val sceneKey = sceneResolver.resolveWindowSceneKey(window, decorView, state)
            !applyPolicy.isReaderBypass(sceneKey, decorView)
        }.getOrDefault(false)
    }

    fun ensureTransparentStatusBarColor(window: Window) {
        if (window.statusBarColor == Color.TRANSPARENT) {
            return
        }
        applying.set(true)
        try {
            window.statusBarColor = Color.TRANSPARENT
        } finally {
            applying.set(false)
        }
    }

    private fun formatColor(color: Int?): String {
        return color?.let { "#%08X".format(Locale.US, it) } ?: "null"
    }

    fun updateReaderMenuSurface(window: Window, hostColor: Int?) {
        if (hostColor == null) {
            windowController.clearReaderStatusBarFrame(window, window.decorView)
            return
        }
        val blockReason = applyPolicy.applyBlockReason(window)
        if (blockReason != null) {
            ModuleFileLogger.throttled(
                key = "reader-direct-filter:${System.identityHashCode(window)}:$blockReason",
                intervalMs = 800L,
                priority = Log.DEBUG,
                tag = logTag,
                message = "reader direct apply filtered rule=$blockReason",
            )
            return
        }
        val decorView = window.decorView
        windowController.applyReaderStatusBarFrame(
            window,
            decorView,
            hostColor,
            updateIconAppearance = true,
        )
    }

    private fun isApplyBlocked(window: Window, reason: String): Boolean {
        val blockReason = applyPolicy.applyBlockReason(window) ?: return false
        if (blockReason != "disabled") {
            ModuleFileLogger.throttled(
                key = "apply-filter:${System.identityHashCode(window)}:$blockReason",
                intervalMs = 800L,
                priority = Log.DEBUG,
                tag = logTag,
                message = "apply filtered reason=$reason rule=$blockReason",
            )
        }
        return true
    }
}

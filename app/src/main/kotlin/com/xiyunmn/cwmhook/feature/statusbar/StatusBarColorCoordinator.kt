package com.xiyunmn.cwmhook.feature.statusbar

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import java.util.Locale

internal class StatusBarColorCoordinator(
    private val sceneRules: StatusBarSceneRules,
    private val colorResolver: StatusBarColorResolver,
    private val colorSampler: StatusBarColorSampler,
    private val scrimController: StatusBarScrimController,
    private val windowController: StatusBarWindowController,
    private val windowRegistry: StatusBarWindowRegistry,
    private val colorCacheProvider: (Context) -> StatusBarColorCache,
    private val readerMenuVisible: (View) -> Boolean,
    private val canSampleWindow: (Window) -> Boolean,
    private val sampleDelayMs: Long,
    private val sampleMinIntervalMs: Long,
    private val logTag: String,
) {
    private val colorStore = StatusBarSceneColorStore(colorCacheProvider)
    private val renderedSampleScheduler = StatusBarRenderedSampleScheduler(
        sceneRules = sceneRules,
        colorResolver = colorResolver,
        colorSampler = colorSampler,
        scrimController = scrimController,
        windowController = windowController,
        windowRegistry = windowRegistry,
        colorStore = colorStore,
        readerMenuVisible = readerMenuVisible,
        canSampleWindow = canSampleWindow,
        sampleDelayMs = sampleDelayMs,
        sampleMinIntervalMs = sampleMinIntervalMs,
        logTag = logTag,
    )

    fun resolveTopSurfaceColor(
        appRoot: View,
        topInset: Int,
        state: StatusBarWindowState,
        fallbackColor: Int,
    ): Int {
        colorResolver.directSceneColor(appRoot.context, state.activeSkinKey, state.activeSceneKey)?.let { color ->
            colorStore.remember(appRoot.context, state, color)
            return color
        }
        val specialColor = when {
            sceneRules.isMyTabScene(state.activeSceneKey) -> colorSampler.sampleMyHeaderColor(appRoot, topInset)
            sceneRules.isReaderScene(state.activeSceneKey) -> colorSampler.sampleReaderMenuColor(appRoot, topInset)
            else -> null
        }
        if (specialColor != null) {
            colorStore.remember(appRoot.context, state, specialColor)
            ModuleFileLogger.throttled(
                key = "special-color:${System.identityHashCode(appRoot)}",
                intervalMs = 500L,
                priority = Log.DEBUG,
                tag = logTag,
                message = "special color skin=${state.activeSkinKey} scene=${state.activeSceneKey} " +
                    "color=${formatColor(specialColor)} previous=${formatColor(fallbackColor)}",
            )
            return specialColor
        }
        if (state.cachedColor != null) {
            return state.cachedColor ?: fallbackColor
        }
        if (sceneRules.isMyTabScene(state.activeSceneKey) || sceneRules.isReaderScene(state.activeSceneKey)) {
            return fallbackColor
        }
        val scannedColor = colorSampler.scanTopBackgroundColor(appRoot, topInset) ?: return fallbackColor
        colorStore.remember(appRoot.context, state, scannedColor)
        ModuleFileLogger.throttled(
            key = "bgscan:${System.identityHashCode(appRoot)}",
            intervalMs = 500L,
            priority = Log.DEBUG,
            tag = logTag,
            message = "background scan skin=${state.activeSkinKey} scene=${state.activeSceneKey} " +
                "color=${formatColor(scannedColor)} previous=${formatColor(fallbackColor)}",
        )
        return scannedColor
    }

    fun scheduleRenderedSample(
        window: Window,
        contentRoot: ViewGroup,
        appRoot: View,
        topInset: Int,
        state: StatusBarWindowState,
        forceSample: Boolean,
    ) {
        renderedSampleScheduler.schedule(window, contentRoot, appRoot, topInset, state, forceSample)
    }

    private fun formatColor(color: Int?): String {
        return color?.let { "#%08X".format(Locale.US, it) } ?: "null"
    }
}

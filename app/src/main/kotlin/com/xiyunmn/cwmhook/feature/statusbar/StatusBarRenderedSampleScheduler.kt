package com.xiyunmn.cwmhook.feature.statusbar

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import java.util.Locale

internal class StatusBarRenderedSampleScheduler(
    private val sceneRules: StatusBarSceneRules,
    private val colorResolver: StatusBarColorResolver,
    private val colorSampler: StatusBarColorSampler,
    private val scrimController: StatusBarScrimController,
    private val windowController: StatusBarWindowController,
    private val windowRegistry: StatusBarWindowRegistry,
    private val colorStore: StatusBarSceneColorStore,
    private val readerMenuVisible: (View) -> Boolean,
    private val canSampleWindow: (Window) -> Boolean,
    private val sampleDelayMs: Long,
    private val sampleMinIntervalMs: Long,
    private val logTag: String,
) {
    fun schedule(
        window: Window,
        contentRoot: ViewGroup,
        appRoot: View,
        topInset: Int,
        state: StatusBarWindowState,
        forceSample: Boolean,
    ) {
        if (state.pendingSample) {
            return
        }
        if (!state.colorDirty && sceneRules.isSpecialColorScene(state.activeSceneKey)) {
            return
        }
        if (!windowRegistry.isForegroundWindow(window, state)) {
            return
        }
        if (!canSampleWindow(window)) {
            return
        }
        if (!forceSample && !state.colorDirty && state.cachedColor != null) {
            return
        }
        val now = SystemClock.uptimeMillis()
        if (!forceSample && now - state.lastSampleAt < sampleMinIntervalMs) {
            return
        }

        val sampleSceneKey = state.activeSceneKey
        val sampleSkinKey = state.activeSkinKey
        val sampleGeneration = state.generation
        state.pendingSample = true
        ModuleViewTaskRegistry.post(appRoot) {
            ModuleViewTaskRegistry.post(appRoot) secondFrame@{
                    state.pendingSample = false
                    if (!canSampleWindow(window)) return@secondFrame
                    state.lastSampleAt = SystemClock.uptimeMillis()
                    if (
                        state.generation != sampleGeneration ||
                        state.activeSceneKey != sampleSceneKey ||
                        state.activeSkinKey != sampleSkinKey
                    ) return@secondFrame
                    if (!windowRegistry.isForegroundWindow(window, state)) return@secondFrame
                    if (sceneRules.isReaderScene(sampleSceneKey) && !readerMenuVisible(window.decorView)) return@secondFrame

                    val sampledColor = colorSampler.sampleSceneTargetColor(appRoot, sampleSceneKey, topInset)
                        ?: return@secondFrame
                    val previous = state.cachedColor
                    colorStore.remember(appRoot.context, state, sampledColor)
                    scrimController.hide(contentRoot)
                    windowController.applySystemBarAppearance(window, window.decorView, sampledColor)
                    ModuleFileLogger.throttled(
                        key = "sample:${System.identityHashCode(window)}",
                        intervalMs = 500L,
                        priority = Log.DEBUG,
                        tag = logTag,
                        message = "${sceneRules.directColorSource(sampleSceneKey, false)} " +
                            "skin=$sampleSkinKey scene=$sampleSceneKey generation=$sampleGeneration " +
                            "color=${formatColor(sampledColor)} previous=${formatColor(previous)}",
                    )
            }
        }
    }

    private fun formatColor(color: Int?): String {
        return color?.let { "#%08X".format(Locale.US, it) } ?: "null"
    }
}

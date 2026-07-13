package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.view.View
import android.view.Window
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import java.lang.reflect.Method

internal class StatusBarFeatureScheduler(
    private val mainFrameActivity: String,
    private val windowRegistry: StatusBarWindowRegistry,
    private val sceneResolver: StatusBarSceneResolver,
    private val runtimeApplier: StatusBarRuntimeApplier,
) {
    fun scheduleApplyForPageView(view: View, reason: String) {
        val activity = windowRegistry.findActivity(view.context) ?: return
        if (activity.javaClass.name == mainFrameActivity) {
            return
        }
        val window = activity.window
        val decorView = window.decorView
        if (!sceneResolver.isLikelyPagePager(view, decorView)) {
            return
        }
        val state = windowRegistry.state(window)
        if (!windowRegistry.isForegroundWindow(window, state)) {
            return
        }
        val sceneKey = sceneResolver.resolvePagerSceneKey(window, view, state)
        scheduleWindowApply(window, decorView, state, reason, sceneKey)
    }

    fun scheduleKnownWindows(reason: String) {
        val windows = windowRegistry.foregroundWindows()
        windows.forEach { window ->
            val decorView = window.decorView
            val state = windowRegistry.state(window)
            state.bumpGeneration(reason)
            ModuleViewTaskRegistry.post(decorView) {
                ModuleViewTaskRegistry.post(decorView) {
                    runtimeApplier.apply(window, reason, forceSample = true)
                }
            }
        }
    }

    fun applyFragmentWindow(fragment: Any?, getActivity: Method, getView: Method?, reason: String) {
        val activity = getActivity.invoke(fragment) as? Activity ?: return
        windowRegistry.rememberActivityWindow(activity)
        val fragmentView = getView?.invoke(fragment) as? View ?: return
        val className = fragment?.javaClass?.name ?: return
        if (className.startsWith("com.bumptech.glide.manager.") || className.contains("RequestManagerFragment")) {
            return
        }
        if (activity.javaClass.name != mainFrameActivity) return
        if (
            className != CiweiMaoClasses.RECOMMEND_FRAGMENT &&
            className != CiweiMaoClasses.RANK_FRAGMENT &&
            className != CiweiMaoClasses.BOOK_SHELF_FRAGMENT &&
            className != CiweiMaoClasses.FIND_FRAGMENT &&
            className != CiweiMaoClasses.MINE_FRAGMENT
        ) return
        val window = activity.window
        val state = windowRegistry.state(window)
        if (!windowRegistry.isForegroundWindow(window, state)) {
            return
        }
        runtimeApplier.apply(
            window,
            reason,
            sceneKeyOverride = sceneResolver.buildFragmentSceneKey(window, fragment, fragmentView),
        )
    }

    private fun scheduleWindowApply(
        window: Window,
        decorView: View,
        state: StatusBarWindowState,
        reason: String,
        sceneKey: String,
    ) {
        state.pendingSceneKey = sceneKey
        if (state.pendingApply) {
            return
        }
        state.pendingApply = true
        ModuleViewTaskRegistry.post(decorView) {
            val nextSceneKey = state.pendingSceneKey ?: sceneKey
            state.pendingSceneKey = null
            state.pendingApply = false
            runtimeApplier.apply(window, reason, sceneKeyOverride = nextSceneKey)
        }
    }
}

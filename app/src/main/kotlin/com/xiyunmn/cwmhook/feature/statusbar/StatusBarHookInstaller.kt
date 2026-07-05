package com.xiyunmn.cwmhook.feature.statusbar

import android.view.View
import android.view.Window
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

internal class StatusBarHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val isApplying: () -> Boolean,
    private val applyWindow: (Window, String, Boolean, String?) -> Unit,
    private val applyIfNeeded: (Window, String) -> Unit,
    private val shouldManageWindow: (Window) -> Boolean,
    private val ensureTransparentStatusBarColor: (Window) -> Unit,
    private val scheduleApplyForPageView: (View, String) -> Unit,
    private val scheduleKnownWindows: (String) -> Unit,
    private val applyFragmentWindow: (Any?, Method, Method?, String) -> Unit,
    private val logTag: String,
) {
    private val activityLifecycleHooks = StatusBarActivityLifecycleHookInstaller(
        windowRegistry = windowRegistry,
        applyWindow = applyWindow,
        logTag = logTag,
    )
    private val deferredHostHooks = StatusBarDeferredHostHookInstaller(
        windowRegistry = windowRegistry,
        applyWindow = applyWindow,
        scheduleApplyForPageView = scheduleApplyForPageView,
        scheduleKnownWindows = scheduleKnownWindows,
        applyFragmentWindow = applyFragmentWindow,
        logTag = logTag,
    )
    private val windowMutationHooks = StatusBarWindowMutationHookInstaller(
        windowRegistry = windowRegistry,
        isApplying = isApplying,
        applyIfNeeded = applyIfNeeded,
        shouldManageWindow = shouldManageWindow,
        ensureTransparentStatusBarColor = ensureTransparentStatusBarColor,
        logTag = logTag,
    )

    fun install(module: XposedModule, classLoader: ClassLoader) {
        activityLifecycleHooks.install(module)
        deferredHostHooks.install(module, classLoader)
        windowMutationHooks.install(module)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        deferredHostHooks.retryDeferredHooks(module, classLoader, reason)
    }
}

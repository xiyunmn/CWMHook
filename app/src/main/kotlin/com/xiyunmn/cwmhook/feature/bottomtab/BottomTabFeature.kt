package com.xiyunmn.cwmhook.feature.bottomtab

import android.app.Activity
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

object BottomTabFeature {
    private const val TAG = "CWMHook.BottomTab"

    fun install(module: XposedModule, classLoader: ClassLoader) {
        BottomTabHookInstaller.install(module, classLoader, ::applyIfMainFrame)
        ModuleFileLogger.i(TAG, "Bottom tab feature installed")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        BottomTabHookInstaller.retryDeferredHooks(module, classLoader, reason, ::applyIfMainFrame)
    }

    fun applyRuntimeConfig(activity: Activity, config: BottomTabConfig, reason: String) {
        if (!BottomTabHostResolver.isMainFrame(activity)) {
            return
        }
        BottomTabRuntimeApplier.clear(activity)
        activity.window.decorView.post {
            BottomTabRuntimeApplier.apply(activity, config, reason)
        }
    }

    private fun applyIfMainFrame(activity: Activity, reason: String) {
        if (!BottomTabHostResolver.isMainFrame(activity)) {
            return
        }
        val config = BottomTabConfigStore.readLocal(activity)
        BottomTabRuntimeApplier.apply(activity, config, reason)
    }
}

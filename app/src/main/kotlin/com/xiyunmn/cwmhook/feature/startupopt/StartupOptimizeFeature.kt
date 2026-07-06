package com.xiyunmn.cwmhook.feature.startupopt

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

object StartupOptimizeFeature {
    private const val TAG = "CWMHook.StartupOptimize"

    fun install(module: XposedModule, classLoader: ClassLoader) {
        StartupOptimizeHookInstaller.install(module, classLoader)
        ModuleFileLogger.i(TAG, "Startup optimize feature install requested")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        StartupOptimizeHookInstaller.retryDeferredHooks(module, classLoader, reason)
    }
}

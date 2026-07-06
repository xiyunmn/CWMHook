package com.xiyunmn.cwmhook.feature.startuptab

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

object StartupTabFeature {
    private const val TAG = "CWMHook.StartupTab"

    fun install(module: XposedModule, classLoader: ClassLoader) {
        StartupTabHookInstaller.install(module, classLoader)
        ModuleFileLogger.i(TAG, "Startup tab feature install requested")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        StartupTabHookInstaller.retryDeferredHooks(module, classLoader, reason)
    }
}

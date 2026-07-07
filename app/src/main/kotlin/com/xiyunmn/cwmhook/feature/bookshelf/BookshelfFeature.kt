package com.xiyunmn.cwmhook.feature.bookshelf

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

object BookshelfFeature {
    private const val TAG = "CWMHook.Bookshelf"
    private val hookInstaller = BookshelfContinueReadingHookInstaller(TAG)

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookInstaller.install(module)
        ModuleFileLogger.i(TAG, "Bookshelf feature install requested")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hookInstaller.install(module)
    }
}

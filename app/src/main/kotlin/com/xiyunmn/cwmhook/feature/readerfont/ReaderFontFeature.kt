package com.xiyunmn.cwmhook.feature.readerfont

import android.app.Activity
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

object ReaderFontFeature {
    private const val TAG = "CWMHook.ReaderFont"

    private val hookInstaller = ReaderFontHookInstaller(TAG)

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookInstaller.install(module, classLoader)
        ModuleFileLogger.i(TAG, "Reader font feature install requested")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hookInstaller.retryDeferredHooks(module, classLoader, reason)
    }

    fun startFontImport(activity: Activity) {
        hookInstaller.startFontImport(activity)
    }
}

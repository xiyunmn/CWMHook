package com.xiyunmn.cwmhook.feature.startupprobe

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

object NativeStartupProbeLoader {
    private const val TAG = "CWMHook.NativeStartupProbe"
    private const val LIBRARY = "cwmhook_startup_probe"

    @Volatile
    private var loaded = false

    fun load() {
        if (loaded) {
            return
        }
        loaded = true
        runCatching {
            System.loadLibrary(LIBRARY)
            ModuleFileLogger.i(TAG, "Native startup probe library loaded")
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Native startup probe library load failed", throwable)
        }
    }
}

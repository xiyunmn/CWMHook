package com.xiyunmn.cwmhook.feature.rewardad

import android.app.Application
import android.content.Context
import com.xiyunmn.cwmhook.config.debug.DebugConfigStore
import com.xiyunmn.cwmhook.config.rewardad.RewardAdSkipConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

object RewardAdSkipFeature {
    private const val TAG = "CWMHook.RewardAdSkip"

    fun install(module: XposedModule, classLoader: ClassLoader) {
        val context = resolveHostContext(classLoader)
        if (context == null) {
            ModuleFileLogger.w(TAG, "Reward ad skip config unavailable; defaulting to disabled")
            RewardAdProbe.configure(false)
            RewardNetworkCapture.configure(false)
            return
        }
        val rewardAdSkipEnabled = RewardAdSkipConfigStore.readLocal(context).enabled
        val probeEnabled = DebugConfigStore.readLocal(context).detailedFileLogEnabled
        RewardAdProbe.configure(probeEnabled)
        RewardNetworkCapture.configure(probeEnabled)
        if (!rewardAdSkipEnabled) {
            ModuleFileLogger.i(TAG, "Reward ad skip disabled by config")
            return
        }
        RewardMissionDialogHookInstaller.install(module, classLoader)
        RewardAdSkipHookInstaller.install(module, classLoader)
        if (probeEnabled) {
            RewardAdProbe.install(module, classLoader)
            RewardNetworkCapture.install(module, classLoader)
        }
        ModuleFileLogger.i(TAG, "Reward ad skip feature install requested")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        val context = resolveHostContext(classLoader)
        if (context == null) {
            ModuleFileLogger.w(TAG, "Reward ad skip config unavailable; retry skipped: $reason")
            RewardAdProbe.configure(false)
            RewardNetworkCapture.configure(false)
            return
        }
        val rewardAdSkipEnabled = RewardAdSkipConfigStore.readLocal(context).enabled
        val probeEnabled = DebugConfigStore.readLocal(context).detailedFileLogEnabled
        RewardAdProbe.configure(probeEnabled)
        RewardNetworkCapture.configure(probeEnabled)
        if (!rewardAdSkipEnabled) {
            ModuleFileLogger.i(TAG, "Reward ad skip retry skipped by config: $reason")
            return
        }
        if (!RewardAdSkipHookInstaller.isInstalled() || !RewardMissionDialogHookInstaller.isInstalled()) {
            ModuleFileLogger.i(TAG, "Retry reward ad skip hook: $reason")
        }
        RewardMissionDialogHookInstaller.install(module, classLoader)
        RewardAdSkipHookInstaller.install(module, classLoader)
        if (probeEnabled) {
            RewardAdProbe.install(module, classLoader)
            RewardNetworkCapture.install(module, classLoader)
        }
    }

    fun prepareForHotReload() {
        RewardAdSkipHookInstaller.clearRuntimeState()
    }

    private fun resolveHostContext(classLoader: ClassLoader): Context? {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread", false, classLoader)
            val currentApplication = activityThread.getDeclaredMethod("currentApplication")
                .also { it.isAccessible = true }
                .invoke(null) as? Application
            currentApplication
        }.getOrNull()
    }
}

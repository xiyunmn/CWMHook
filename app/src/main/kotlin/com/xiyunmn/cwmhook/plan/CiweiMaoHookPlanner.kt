package com.xiyunmn.cwmhook.plan

import com.xiyunmn.cwmhook.app.FloatingModulePanelFeature
import com.xiyunmn.cwmhook.feature.bottomtab.BottomTabFeature
import com.xiyunmn.cwmhook.feature.readerfont.ReaderFontFeature
import com.xiyunmn.cwmhook.feature.statusbar.ImmersiveStatusBarFeature
import io.github.libxposed.api.XposedModule

object CiweiMaoHookPlanner {
    fun packageReadyPlan(module: XposedModule, processName: String): HookInstallPlan {
        return HookInstallPlan(
            processName = processName,
            phase = "packageReady",
            entries = listOf(
                HookInstallEntry("ImmersiveStatusBarFeature.install") { classLoader ->
                    ImmersiveStatusBarFeature.install(module, classLoader)
                },
                HookInstallEntry("BottomTabFeature.install") { classLoader ->
                    BottomTabFeature.install(module, classLoader)
                },
                HookInstallEntry("ReaderFontFeature.install") { classLoader ->
                    ReaderFontFeature.install(module, classLoader)
                },
                HookInstallEntry("FloatingModulePanelFeature.install") { classLoader ->
                    FloatingModulePanelFeature.install(module, classLoader)
                },
            ),
        )
    }

    fun applicationReadyRetryPlan(
        module: XposedModule,
        processName: String,
        reason: String,
    ): HookInstallPlan {
        return HookInstallPlan(
            processName = processName,
            phase = "applicationReadyRetry",
            entries = listOf(
                HookInstallEntry("ImmersiveStatusBarFeature.retryDeferredHooks") { classLoader ->
                    ImmersiveStatusBarFeature.retryDeferredHooks(module, classLoader, reason)
                },
                HookInstallEntry("BottomTabFeature.retryDeferredHooks") { classLoader ->
                    BottomTabFeature.retryDeferredHooks(module, classLoader, reason)
                },
                HookInstallEntry("ReaderFontFeature.retryDeferredHooks") { classLoader ->
                    ReaderFontFeature.retryDeferredHooks(module, classLoader, reason)
                },
                HookInstallEntry("FloatingModulePanelFeature.retryDeferredHooks") { classLoader ->
                    FloatingModulePanelFeature.retryDeferredHooks(module, classLoader, reason)
                },
            ),
        )
    }
}

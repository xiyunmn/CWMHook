package com.xiyunmn.cwmhook.plan

import com.xiyunmn.cwmhook.app.FloatingModulePanelFeature
import com.xiyunmn.cwmhook.feature.autosignin.AutoSignInFeature
import com.xiyunmn.cwmhook.feature.bottomtab.BottomTabFeature
import com.xiyunmn.cwmhook.feature.chapterbackup.ChapterBackupFeature
import com.xiyunmn.cwmhook.feature.readerfont.ReaderFontFeature
import com.xiyunmn.cwmhook.feature.startuptab.StartupTabFeature
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
                HookInstallEntry("StartupTabFeature.install") { classLoader ->
                    StartupTabFeature.install(module, classLoader)
                },
                HookInstallEntry("ReaderFontFeature.install") { classLoader ->
                    ReaderFontFeature.install(module, classLoader)
                },
                HookInstallEntry("AutoSignInFeature.install") { classLoader ->
                    AutoSignInFeature.install(module, classLoader)
                },
                HookInstallEntry("ChapterBackupFeature.install") { classLoader ->
                    ChapterBackupFeature.install(module, classLoader)
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
                HookInstallEntry("StartupTabFeature.retryDeferredHooks") { classLoader ->
                    StartupTabFeature.retryDeferredHooks(module, classLoader, reason)
                },
                HookInstallEntry("ReaderFontFeature.retryDeferredHooks") { classLoader ->
                    ReaderFontFeature.retryDeferredHooks(module, classLoader, reason)
                },
                HookInstallEntry("AutoSignInFeature.retryDeferredHooks") { classLoader ->
                    AutoSignInFeature.retryDeferredHooks(module, classLoader, reason)
                },
                HookInstallEntry("ChapterBackupFeature.retryDeferredHooks") { classLoader ->
                    ChapterBackupFeature.retryDeferredHooks(module, classLoader, reason)
                },
                HookInstallEntry("FloatingModulePanelFeature.retryDeferredHooks") { classLoader ->
                    FloatingModulePanelFeature.retryDeferredHooks(module, classLoader, reason)
                },
            ),
        )
    }
}

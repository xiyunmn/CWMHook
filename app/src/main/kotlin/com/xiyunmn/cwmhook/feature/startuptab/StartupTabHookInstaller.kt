package com.xiyunmn.cwmhook.feature.startuptab

import android.app.Activity
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import com.xiyunmn.cwmhook.host.CiweiMaoMembers
import io.github.libxposed.api.XposedModule

object StartupTabHookInstaller {
    private const val TAG = "CWMHook.StartupTab"

    private var installed = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (installed) {
            return
        }
        val frameClass = try {
            Class.forName(CiweiMaoClasses.DG_FRAME_ACTIVITY, false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(TAG, "DGFrameActivity not visible yet, startup tab hook deferred")
            return
        }
        val method = runCatching {
            frameClass.getDeclaredMethod(CiweiMaoMembers.DG_FRAME_INIT_WIDGETS).also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "DGFrameActivity.initWidgets not found", throwable)
            return
        }
        val hooked = XposedCompat.interceptProtective(module, method, "$TAG.DGFrameActivity.initWidgets") { chain ->
            (chain.thisObject as? Activity)?.let(StartupTabSelector::prepareBeforeInitWidgets)
            chain.proceed()
        }
        if (!hooked) {
            return
        }
        installed = true
        ModuleFileLogger.i(TAG, "Startup tab hook installed")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        if (installed) {
            return
        }
        ModuleFileLogger.i(TAG, "Retry startup tab hook: $reason")
        install(module, classLoader)
    }
}

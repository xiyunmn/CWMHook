package com.xiyunmn.cwmhook.feature.bottomtab

import android.app.Activity
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule

object BottomTabHookInstaller {
    private const val TAG = "CWMHook.BottomTab"

    private var widgetHookInstalled = false
    private var activityHookInstalled = false

    fun install(
        module: XposedModule,
        classLoader: ClassLoader,
        onActivityReady: (Activity, String) -> Unit,
    ) {
        hookActivityResume(module, onActivityReady)
        hookFrameWidgets(module, classLoader, onActivityReady)
    }

    fun retryDeferredHooks(
        module: XposedModule,
        classLoader: ClassLoader,
        reason: String,
        onActivityReady: (Activity, String) -> Unit,
    ) {
        if (!widgetHookInstalled) {
            ModuleFileLogger.i(TAG, "Retry bottom tab widget hook: $reason")
            hookFrameWidgets(module, classLoader, onActivityReady)
        }
    }

    private fun hookFrameWidgets(
        module: XposedModule,
        classLoader: ClassLoader,
        onActivityReady: (Activity, String) -> Unit,
    ) {
        if (widgetHookInstalled) {
            return
        }
        val frameClass = try {
            Class.forName(CiweiMaoClasses.DG_FRAME_ACTIVITY, false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(TAG, "DGFrameActivity not visible yet, bottom tab hook deferred")
            return
        }
        val method = runCatching {
            frameClass.getDeclaredMethod("initWidgets").also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "DGFrameActivity.initWidgets not found", throwable)
            return
        }
        val hooked = XposedCompat.hookAfter(module, method, "$TAG.DGFrameActivity.initWidgets") { chain ->
            (chain.thisObject as? Activity)?.let { onActivityReady(it, "DGFrameActivity.initWidgets") }
        }
        if (!hooked) {
            return
        }
        widgetHookInstalled = true
        ModuleFileLogger.i(TAG, "Bottom tab widget hook installed")
    }

    private fun hookActivityResume(
        module: XposedModule,
        onActivityReady: (Activity, String) -> Unit,
    ) {
        if (activityHookInstalled) {
            return
        }
        val hooked = XposedCompat.hookAfter(module, Activity::class.java.getDeclaredMethod("onPostResume"), "$TAG.Activity.onPostResume") { chain ->
            (chain.thisObject as? Activity)?.let { onActivityReady(it, "Activity.onPostResume") }
        }
        if (!hooked) {
            return
        }
        activityHookInstalled = true
    }
}

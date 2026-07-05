package com.xiyunmn.cwmhook.feature.panel

import android.app.Activity
import android.view.KeyEvent
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

internal class FloatingPanelHookInstaller(
    private val logTag: String,
    private val onFragmentEntryReady: (Any) -> Unit,
    private val onMainFrameActivityReady: (Activity, String) -> Unit,
    private val isBackKey: (KeyEvent?) -> Boolean,
    private val hasPanel: (Activity) -> Boolean,
    private val closePanel: (Activity, String) -> Boolean,
) {
    private var recommendHookInstalled = false
    private var activityHookInstalled = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookMainActivityLifecycle(module)
        hookRecommendEntry(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hookMainActivityLifecycle(module)
        if (!recommendHookInstalled) {
            ModuleFileLogger.i(logTag, "Retry floating panel hook: $reason")
            hookRecommendEntry(module, classLoader)
        }
    }

    private fun hookRecommendEntry(module: XposedModule, classLoader: ClassLoader) {
        if (recommendHookInstalled) {
            return
        }
        val fragmentClass = try {
            Class.forName(CiweiMaoClasses.RECOMMEND_FRAGMENT, false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(logTag, "FragRecommendNew not visible yet, floating panel hook deferred")
            return
        }
        val method = runCatching {
            fragmentClass.getDeclaredMethod("initView").also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(logTag, "FragRecommendNew.initView not found", throwable)
            return
        }

        val hooked = hookAfter(module, method) { chain ->
            onFragmentEntryReady(chain.thisObject)
        }
        if (!hooked) {
            return
        }
        recommendHookInstalled = true
        ModuleFileLogger.i(logTag, "Floating panel entry hook installed")
    }

    private fun hookMainActivityLifecycle(module: XposedModule) {
        if (activityHookInstalled) {
            return
        }
        val resumeHooked = hookAfter(module, Activity::class.java.getDeclaredMethod("onPostResume")) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            if (activity.javaClass.name == CiweiMaoClasses.MAIN_FRAME_ACTIVITY) {
                activity.window.decorView.post {
                    onMainFrameActivityReady(activity, "activity:onPostResume")
                }
            }
        }
        val focusHooked = hookAfter(module, Activity::class.java.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType)) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val hasFocus = chain.args.firstOrNull() as? Boolean ?: return@hookAfter
            if (hasFocus && activity.javaClass.name == CiweiMaoClasses.MAIN_FRAME_ACTIVITY) {
                activity.window.decorView.post {
                    onMainFrameActivityReady(activity, "activity:onWindowFocus")
                }
            }
        }
        val backHooked = hookBackClose(module)
        if (!resumeHooked || !focusHooked || !backHooked) {
            return
        }
        activityHookInstalled = true
        ModuleFileLogger.i(logTag, "Floating panel Activity lifecycle hooks installed")
    }

    private fun hookBackClose(module: XposedModule): Boolean {
        val dispatchHooked = XposedCompat.interceptProtective(
            module,
            Activity::class.java.getDeclaredMethod("dispatchKeyEvent", KeyEvent::class.java),
            "$logTag.Activity.dispatchKeyEvent",
        ) { chain ->
            val activity = chain.thisObject as? Activity
            val event = chain.args.firstOrNull() as? KeyEvent
            if (activity != null && isBackKey(event) && hasPanel(activity)) {
                if (event?.action == KeyEvent.ACTION_UP) {
                    closePanel(activity, "dispatchKeyEvent")
                }
                true
            } else {
                chain.proceed()
            }
        }

        val backPressedHooked = XposedCompat.interceptProtective(
            module,
            Activity::class.java.getDeclaredMethod("onBackPressed"),
            "$logTag.Activity.onBackPressed",
        ) { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && closePanel(activity, "onBackPressed")) {
                null
            } else {
                chain.proceed()
            }
        }
        return dispatchHooked && backPressedHooked
    }

    private fun hookAfter(
        module: XposedModule,
        executable: Executable,
        after: (XposedInterface.Chain) -> Unit,
    ): Boolean {
        val feature = "$logTag.${executable.declaringClass.name}.${executable.name}"
        return XposedCompat.hookAfter(module, executable, feature, after)
    }
}

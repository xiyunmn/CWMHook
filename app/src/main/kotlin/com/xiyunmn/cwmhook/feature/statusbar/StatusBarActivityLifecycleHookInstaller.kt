package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.os.Bundle
import android.content.res.Configuration
import android.view.Window
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import io.github.libxposed.api.XposedModule

internal class StatusBarActivityLifecycleHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val applyWindow: (Window, String, Boolean, String?) -> Unit,
    private val isReaderActivity: (String) -> Boolean,
    private val logTag: String,
) {
    fun install(module: XposedModule) {
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java),
            "$logTag.Activity.onCreate",
        ) { chain ->
            (chain.thisObject as? Activity)?.let {
                if (isReaderActivity(it.javaClass.name)) return@let
                windowRegistry.rememberActivityWindow(it)
                applyWindow(it.window, "Activity.onCreate", false, null)
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onConfigurationChanged", Configuration::class.java),
            "$logTag.Activity.onConfigurationChanged",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            if (isReaderActivity(activity.javaClass.name)) return@hookAfter
            windowRegistry.rememberActivityWindow(activity)
            val state = windowRegistry.state(activity.window)
            state.markDirty(clearCached = false)
            val decor = activity.window.decorView
            ModuleViewTaskRegistry.post(decor) {
                ModuleViewTaskRegistry.post(decor) {
                    applyWindow(activity.window, "Activity.onConfigurationChanged", true, null)
                }
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onContentChanged"),
            "$logTag.Activity.onContentChanged",
        ) { chain ->
            (chain.thisObject as? Activity)?.let {
                if (isReaderActivity(it.javaClass.name)) return@let
                windowRegistry.rememberActivityWindow(it)
                applyWindow(it.window, "Activity.onContentChanged", false, null)
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onResume"),
            "$logTag.Activity.onResume",
        ) { chain ->
            (chain.thisObject as? Activity)?.let {
                if (isReaderActivity(it.javaClass.name)) return@let
                windowRegistry.rememberActivityWindow(it)
                windowRegistry.setForegroundWindow(it.window)
                windowRegistry.state(it.window).bumpGeneration("Activity.onResume")
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onPostResume"),
            "$logTag.Activity.onPostResume",
        ) { chain ->
            (chain.thisObject as? Activity)?.let {
                if (isReaderActivity(it.javaClass.name)) return@let
                windowRegistry.rememberActivityWindow(it)
                windowRegistry.setForegroundWindow(it.window)
                ModuleViewTaskRegistry.post(it.window.decorView) {
                    applyWindow(it.window, "Activity.onPostResume", false, null)
                }
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onPause"),
            "$logTag.Activity.onPause",
        ) { chain ->
            (chain.thisObject as? Activity)?.let {
                windowRegistry.state(it.window).bumpGeneration("Activity.onPause")
                windowRegistry.clearForegroundWindow(it.window)
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onDestroy"),
            "$logTag.Activity.onDestroy",
        ) { chain ->
            (chain.thisObject as? Activity)?.let(windowRegistry::unregisterActivityWindow)
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType),
            "$logTag.Activity.onWindowFocusChanged",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            if (isReaderActivity(activity.javaClass.name)) return@hookAfter
            val hasFocus = chain.getArg(0) as? Boolean ?: return@hookAfter
            windowRegistry.rememberActivityWindow(activity)
            windowRegistry.state(activity.window).setFocus(hasFocus)
            if (hasFocus) {
                windowRegistry.setForegroundWindow(activity.window)
                val decor = activity.window.decorView
                ModuleViewTaskRegistry.post(decor) {
                    ModuleViewTaskRegistry.post(decor) {
                        applyWindow(activity.window, "Activity.onWindowFocusChanged", false, null)
                    }
                }
            } else {
                windowRegistry.clearForegroundWindow(activity.window)
            }
        }
    }
}

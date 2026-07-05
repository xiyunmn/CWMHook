package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.os.Bundle
import android.view.Window
import com.xiyunmn.cwmhook.core.XposedCompat
import io.github.libxposed.api.XposedModule

internal class StatusBarActivityLifecycleHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val applyWindow: (Window, String, Boolean, String?) -> Unit,
    private val logTag: String,
) {
    fun install(module: XposedModule) {
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java),
            "$logTag.Activity.onCreate",
        ) { chain ->
            (chain.thisObject as? Activity)?.let {
                windowRegistry.rememberActivityWindow(it)
                applyWindow(it.window, "Activity.onCreate", false, null)
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onContentChanged"),
            "$logTag.Activity.onContentChanged",
        ) { chain ->
            (chain.thisObject as? Activity)?.let {
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
                windowRegistry.rememberActivityWindow(it)
                windowRegistry.setForegroundWindow(it.window)
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onPostResume"),
            "$logTag.Activity.onPostResume",
        ) { chain ->
            (chain.thisObject as? Activity)?.let {
                windowRegistry.rememberActivityWindow(it)
                windowRegistry.setForegroundWindow(it.window)
            }
        }
        XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType),
            "$logTag.Activity.onWindowFocusChanged",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val hasFocus = chain.getArg(0) as? Boolean ?: return@hookAfter
            windowRegistry.rememberActivityWindow(activity)
            windowRegistry.state(activity.window).setFocus(hasFocus)
            if (hasFocus) {
                windowRegistry.setForegroundWindow(activity.window)
            } else {
                windowRegistry.clearForegroundWindow(activity.window)
            }
        }
    }
}

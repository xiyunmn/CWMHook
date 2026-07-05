package com.xiyunmn.cwmhook.feature.autosignin

import android.app.Activity
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

internal class AutoSignInHookInstaller(
    private val onActivityReady: (Activity, String) -> Unit,
) {
    private var installed = false

    fun install(module: XposedModule) {
        if (installed) {
            return
        }
        val resumeHooked = XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onPostResume"),
            "$TAG.Activity.onPostResume",
        ) { chain ->
            (chain.thisObject as? Activity)?.let { activity ->
                onActivityReady(activity, "Activity.onPostResume")
            }
        }
        val focusHooked = XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType),
            "$TAG.Activity.onWindowFocusChanged",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val hasFocus = chain.getArg(0) as? Boolean ?: return@hookAfter
            if (hasFocus) {
                onActivityReady(activity, "Activity.onWindowFocusChanged")
            }
        }
        if (resumeHooked && focusHooked) {
            installed = true
            ModuleFileLogger.i(TAG, "Auto sign-in lifecycle hooks installed")
        }
    }

    private companion object {
        const val TAG = "CWMHook.AutoSignInHook"
    }
}

package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import com.xiyunmn.cwmhook.core.XposedCompat
import io.github.libxposed.api.XposedModule

internal class StatusBarWindowMutationHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val isApplying: () -> Boolean,
    private val applyIfNeeded: (Window, String) -> Unit,
    private val shouldManageWindow: (Window) -> Boolean,
    private val ensureTransparentStatusBarColor: (Window) -> Unit,
    private val logTag: String,
) {
    fun install(module: XposedModule) {
        hookStatusBarColor(module)
        hookDecorFitsSystemWindows(module)
        hookSystemUiVisibility(module)
    }

    private fun hookStatusBarColor(module: XposedModule) {
        val method = runCatching {
            Class.forName(PHONE_WINDOW_CLASS)
                .getDeclaredMethod("setStatusBarColor", Int::class.javaPrimitiveType)
                .also { it.isAccessible = true }
        }.getOrElse {
            return
        }
        XposedCompat.interceptProtective(
            module,
            method,
            "$logTag.PhoneWindow.setStatusBarColor",
        ) { chain ->
            val window = chain.thisObject as? Window
            val color = chain.getArg(0) as? Int
            if (
                window != null &&
                color != null &&
                !isApplying() &&
                color != Color.TRANSPARENT &&
                shouldManageWindow(window)
            ) {
                ensureTransparentStatusBarColor(window)
                applyIfNeeded(window, "PhoneWindow.setStatusBarColor")
                null
            } else {
                val result = chain.proceed()
                if (!isApplying()) {
                    window?.let { applyIfNeeded(it, "PhoneWindow.setStatusBarColor") }
                }
                result
            }
        }
    }

    private fun hookDecorFitsSystemWindows(module: XposedModule) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        XposedCompat.interceptProtective(
            module,
            Window::class.java.getDeclaredMethod("setDecorFitsSystemWindows", Boolean::class.javaPrimitiveType),
            "$logTag.Window.setDecorFitsSystemWindows",
        ) { chain ->
            val window = chain.thisObject as? Window
            val fitsSystemWindows = chain.getArg(0) as? Boolean
            if (
                window != null &&
                fitsSystemWindows == true &&
                !isApplying() &&
                shouldManageWindow(window)
            ) {
                applyIfNeeded(window, "Window.setDecorFitsSystemWindows")
                null
            } else {
                val result = chain.proceed()
                if (!isApplying()) {
                    window?.let { applyIfNeeded(it, "Window.setDecorFitsSystemWindows") }
                }
                result
            }
        }
    }

    private fun hookSystemUiVisibility(module: XposedModule) {
        XposedCompat.hookAfter(
            module,
            View::class.java.getDeclaredMethod("setSystemUiVisibility", Int::class.javaPrimitiveType),
            "$logTag.View.setSystemUiVisibility",
        ) { chain ->
            if (isApplying()) {
                return@hookAfter
            }
            val view = chain.thisObject as? View ?: return@hookAfter
            windowRegistry.findActivity(view.context)?.let { applyIfNeeded(it.window, "View.setSystemUiVisibility") }
        }
    }

    private companion object {
        const val PHONE_WINDOW_CLASS = "com.android.internal.policy.PhoneWindow"
    }
}

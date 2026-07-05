package com.xiyunmn.cwmhook.feature.statusbar

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

internal class StatusBarFragmentLifecycleHookInstaller(
    private val applyFragmentWindow: (Any?, Method, Method?, String) -> Unit,
    private val hookHelper: StatusBarHostHookHelper,
    private val logTag: String,
) {
    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        val fragmentClass = try {
            Class.forName("androidx.fragment.app.Fragment", false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(logTag, "androidx.fragment.app.Fragment not visible yet, fragment hooks deferred")
            return false
        }
        val getActivity = fragmentClass.getMethod("getActivity")
        val getView = fragmentClass.getMethod("getView")
        var installed = false

        hookHelper.hookMethodIfPresent(module, fragmentClass, "onResume") { chain ->
            applyFragmentWindow(chain.thisObject, getActivity, getView, "Fragment.onResume")
        }.also { installed = installed || it }

        hookHelper.hookMethodIfPresent(module, fragmentClass, "onHiddenChanged", Boolean::class.javaPrimitiveType!!) { chain ->
            val hidden = chain.getArg(0) as? Boolean ?: return@hookMethodIfPresent
            if (!hidden) {
                applyFragmentWindow(chain.thisObject, getActivity, getView, "Fragment.onHiddenChanged")
            }
        }.also { installed = installed || it }

        hookHelper.hookMethodIfPresent(module, fragmentClass, "setUserVisibleHint", Boolean::class.javaPrimitiveType!!) { chain ->
            val visible = chain.getArg(0) as? Boolean ?: return@hookMethodIfPresent
            if (visible) {
                applyFragmentWindow(chain.thisObject, getActivity, getView, "Fragment.setUserVisibleHint")
            }
        }.also { installed = installed || it }

        if (installed) {
            ModuleFileLogger.i(logTag, "Fragment hooks installed")
        }
        return installed
    }
}

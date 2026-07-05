package com.xiyunmn.cwmhook.feature.statusbar

import android.view.View
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

internal class StatusBarViewPagerHookInstaller(
    private val scheduleApplyForPageView: (View, String) -> Unit,
    private val hookHelper: StatusBarHostHookHelper,
    private val logTag: String,
) {
    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        val viewPagerClass = try {
            Class.forName("androidx.viewpager.widget.ViewPager", false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(logTag, "androidx.viewpager.widget.ViewPager not visible yet, page switch hook deferred")
            return false
        }
        var installed = false
        hookHelper.hookMethodIfPresent(module, viewPagerClass, "setCurrentItem", Int::class.javaPrimitiveType!!) { chain ->
            (chain.thisObject as? View)?.let { scheduleApplyForPageView(it, "ViewPager.setCurrentItem") }
        }.also { installed = installed || it }
        hookHelper.hookMethodIfPresent(
            module,
            viewPagerClass,
            "setCurrentItem",
            Int::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
        ) { chain ->
            (chain.thisObject as? View)?.let { scheduleApplyForPageView(it, "ViewPager.setCurrentItemSmooth") }
        }.also { installed = installed || it }

        if (installed) {
            ModuleFileLogger.i(logTag, "ViewPager hooks installed")
        }
        return installed
    }
}

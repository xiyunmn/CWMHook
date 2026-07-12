package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule

internal class StatusBarBookDetailHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val applyWindow: (android.view.Window, String, Boolean, String?) -> Unit,
    private val hookHelper: StatusBarHostHookHelper,
    private val logTag: String,
) {
    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        val listenerClass = try {
            Class.forName("${CiweiMaoClasses.BOOK_DETAIL_ACTIVITY}\$7", false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(logTag, "BookDetail scroll listener not visible yet, hook deferred")
            return false
        }
        val installed = hookHelper.hookMethodIfPresent(
            module,
            listenerClass,
            "onScroll",
            Int::class.javaPrimitiveType!!,
        ) { chain ->
            val listener = chain.thisObject ?: return@hookMethodIfPresent
            val activity = findOuterActivity(listener) ?: return@hookMethodIfPresent
            windowRegistry.rememberActivityWindow(activity)
            val state = windowRegistry.state(activity.window)
            state.bumpGeneration("BookDetail.onScroll")
            activity.window.decorView.post {
                applyWindow(activity.window, "BookDetail.onScroll", true, null)
            }
        }
        if (installed) ModuleFileLogger.i(logTag, "BookDetail scroll hook installed")
        return installed
    }

    private fun findOuterActivity(listener: Any): Activity? {
        return listener.javaClass.declaredFields.asSequence()
            .onEach { it.isAccessible = true }
            .mapNotNull { runCatching { it.get(listener) as? Activity }.getOrNull() }
            .firstOrNull { it.javaClass.name == CiweiMaoClasses.BOOK_DETAIL_ACTIVITY }
    }
}

package com.xiyunmn.cwmhook.feature.statusbar

import android.view.View
import android.view.Window
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule

internal class StatusBarReaderMenuHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val applyWindow: (Window, String, Boolean, String?) -> Unit,
    private val hookHelper: StatusBarHostHookHelper,
    private val logTag: String,
) {
    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        val titleBarClass = try {
            Class.forName(CiweiMaoClasses.READER_TITLE_BAR, false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(logTag, "ReaderTitleBar not visible yet, reader menu hook deferred")
            return false
        }
        val installed = hookHelper.hookMethodIfPresent(module, titleBarClass, "setVisibility", Int::class.javaPrimitiveType!!) { chain ->
            val titleBar = chain.thisObject as? View ?: return@hookMethodIfPresent
            titleBar.post {
                windowRegistry.findActivity(titleBar.context)?.let { applyWindow(it.window, "ReaderTitleBar.setVisibility", true, null) }
            }
        }
        if (installed) {
            ModuleFileLogger.i(logTag, "ReaderTitleBar hooks installed")
        }
        return installed
    }
}

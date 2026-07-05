package com.xiyunmn.cwmhook.feature.statusbar

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule

internal class StatusBarSkinChangeHookInstaller(
    private val scheduleKnownWindows: (String) -> Unit,
    private val hookHelper: StatusBarHostHookHelper,
    private val logTag: String,
) {
    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        val helperClass = try {
            Class.forName(CiweiMaoClasses.SKIN_CHANGE_HELPER, false, classLoader)
        } catch (_: Throwable) {
            return false
        }
        val listenerClass = Class.forName(
            CiweiMaoClasses.SKIN_CHANGE_LISTENER,
            false,
            classLoader,
        )
        var installed = false
        installed = hookHelper.hookMethodIfPresent(module, helperClass, "switchSkinMode", String::class.java, listenerClass) {
            scheduleKnownWindows("SkinChangeHelper.switchSkinMode")
        } || installed
        installed = hookHelper.hookMethodIfPresent(module, helperClass, "refreshSkin", listenerClass) {
            scheduleKnownWindows("SkinChangeHelper.refreshSkin")
        } || installed

        if (installed) {
            ModuleFileLogger.i(logTag, "SkinChangeHelper hooks installed")
        }
        return installed
    }
}

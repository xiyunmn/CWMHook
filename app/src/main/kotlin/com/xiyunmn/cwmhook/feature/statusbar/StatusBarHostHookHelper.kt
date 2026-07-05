package com.xiyunmn.cwmhook.feature.statusbar

import com.xiyunmn.cwmhook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

internal class StatusBarHostHookHelper(
    private val logTag: String,
) {
    fun hookMethodIfPresent(
        module: XposedModule,
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
        after: (XposedInterface.Chain) -> Unit,
    ): Boolean {
        val method = runCatching { clazz.getDeclaredMethod(name, *parameterTypes) }.getOrNull() ?: return false
        return hookAfter(module, method, after)
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

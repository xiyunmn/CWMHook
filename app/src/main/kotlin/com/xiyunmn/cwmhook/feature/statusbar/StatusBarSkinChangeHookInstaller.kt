package com.xiyunmn.cwmhook.feature.statusbar

import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

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
        listOf(
            helperClass.getDeclaredMethod("switchSkinMode", String::class.java, listenerClass),
            helperClass.getDeclaredMethod("refreshSkin", listenerClass),
        ).forEach { method ->
            val listenerIndex = method.parameterTypes.lastIndex
            installed = XposedCompat.interceptProtective(
                module,
                method,
                "$logTag.SkinChangeHelper.${method.name}",
            ) { chain ->
                val args = chain.getArgs().toTypedArray()
                val original = args.getOrNull(listenerIndex)
                if (original != null) {
                    args[listenerIndex] = Proxy.newProxyInstance(
                        listenerClass.classLoader,
                        arrayOf(listenerClass),
                    ) { _, callback, callbackArgs ->
                        val result = try {
                            callback.invoke(original, *(callbackArgs ?: emptyArray()))
                        } catch (throwable: InvocationTargetException) {
                            throw throwable.targetException
                        }
                        if (callback.name == "onSuccess") {
                            scheduleKnownWindows("SkinChangeHelper.${method.name}.onSuccess")
                        }
                        result
                    }
                }
                val result = chain.proceed(args)
                if (original == null) {
                    scheduleKnownWindows("SkinChangeHelper.${method.name}.noListener")
                }
                result
            } || installed
        }

        if (installed) {
            ModuleFileLogger.i(logTag, "SkinChangeHelper hooks installed")
        }
        return installed
    }
}

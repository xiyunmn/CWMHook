package com.xiyunmn.cwmhook.core

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

object XposedCompat {
    private const val TAG = "CWMHook.XposedCompat"

    @Volatile
    var module: XposedModule? = null
        private set

    fun attach(module: XposedModule) {
        this.module = module
    }

    fun hookAfter(
        executable: Executable,
        feature: String,
        after: (XposedInterface.Chain) -> Unit,
    ): Boolean {
        val activeModule = module ?: run {
            ModuleFileLogger.w(TAG, "hookAfter skipped, module unavailable: $feature")
            return false
        }
        return hookAfter(activeModule, executable, feature, after)
    }

    fun hookAfter(
        module: XposedModule,
        executable: Executable,
        feature: String,
        after: (XposedInterface.Chain) -> Unit,
    ): Boolean {
        return runCatching {
            module.hook(executable)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    after(chain)
                    result
                }
            true
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "hookAfter failed: $feature", throwable)
            false
        }
    }

    fun interceptProtective(
        executable: Executable,
        feature: String,
        intercept: (XposedInterface.Chain) -> Any?,
    ): Boolean {
        val activeModule = module ?: run {
            ModuleFileLogger.w(TAG, "intercept skipped, module unavailable: $feature")
            return false
        }
        return interceptProtective(activeModule, executable, feature, intercept)
    }

    fun interceptProtective(
        module: XposedModule,
        executable: Executable,
        feature: String,
        intercept: (XposedInterface.Chain) -> Any?,
    ): Boolean {
        return runCatching {
            module.hook(executable)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain -> intercept(chain) }
            true
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "intercept failed: $feature", throwable)
            false
        }
    }

    fun findClassOrNull(name: String, classLoader: ClassLoader): Class<*>? {
        return runCatching { Class.forName(name, false, classLoader) }.getOrNull()
    }
}

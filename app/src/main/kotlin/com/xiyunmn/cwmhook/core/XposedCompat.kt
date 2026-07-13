package com.xiyunmn.cwmhook.core

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap

object XposedCompat {
    private const val TAG = "CWMHook.XposedCompat"

    @Volatile
    var module: XposedModule? = null
        private set

    @Volatile
    private var retiring = false

    private val replacementLock = Any()
    private var oldHandlesById: MutableMap<String, ArrayDeque<XposedInterface.HookHandle>>? = null
    private var consumedOldHandles: MutableSet<XposedInterface.HookHandle>? = null

    fun attach(module: XposedModule) {
        this.module = module
        retiring = false
    }

    fun beginHotReload() {
        retiring = true
    }

    fun cancelHotReload() {
        retiring = false
    }

    fun isRetiring(): Boolean = retiring

    fun beginHookReplacement(oldHandles: List<XposedInterface.HookHandle>) {
        synchronized(replacementLock) {
            oldHandlesById = buildMap<String, ArrayDeque<XposedInterface.HookHandle>> {
                oldHandles.forEach { handle ->
                    val id = runCatching { handle.id }.getOrNull() ?: return@forEach
                    getOrPut(id) { ArrayDeque() }.addLast(handle)
                }
            }.toMutableMap()
            consumedOldHandles = Collections.newSetFromMap(IdentityHashMap())
        }
    }

    fun finishHookReplacement(oldHandles: List<XposedInterface.HookHandle>) {
        val consumed = synchronized(replacementLock) {
            val result = consumedOldHandles.orEmpty().toSet()
            oldHandlesById = null
            consumedOldHandles = null
            result
        }
        oldHandles.forEach { handle ->
            if (handle !in consumed) {
                runCatching { handle.unhook() }
                    .onFailure { throwable ->
                        ModuleFileLogger.w(TAG, "Failed to remove obsolete hot-reload hook id=${runCatching { handle.id }.getOrNull()}", throwable)
                    }
            }
        }
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
        return installHook(module, executable, feature) { chain ->
            if (retiring) {
                chain.proceed()
            } else {
                    val result = chain.proceed()
                    after(chain)
                    result
                }
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
        return installHook(module, executable, feature) { chain ->
            if (retiring) chain.proceed() else intercept(chain)
        }
    }

    fun findClassOrNull(name: String, classLoader: ClassLoader): Class<*>? {
        return runCatching { Class.forName(name, false, classLoader) }.getOrNull()
    }

    private fun installHook(
        module: XposedModule,
        executable: Executable,
        feature: String,
        hooker: XposedInterface.Hooker,
    ): Boolean {
        val id = hookId(feature, executable)
        return runCatching {
            val oldHandle = takeOldHandle(id)
            if (oldHandle != null) {
                oldHandle.replaceHook(hooker)
                synchronized(replacementLock) {
                    consumedOldHandles?.add(oldHandle)
                }
                ModuleFileLogger.i(TAG, "Hook atomically replaced: $id")
            } else {
                module.hook(executable)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId(id)
                    .intercept(hooker)
            }
            true
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "Hook install failed: feature=$feature id=$id", throwable)
            false
        }
    }

    private fun takeOldHandle(id: String): XposedInterface.HookHandle? {
        return synchronized(replacementLock) {
            val queue = oldHandlesById?.get(id) ?: return@synchronized null
            queue.removeFirstOrNull().also {
                if (queue.isEmpty()) {
                    oldHandlesById?.remove(id)
                }
            }
        }
    }

    private fun hookId(feature: String, executable: Executable): String {
        val parameters = executable.parameterTypes.joinToString(",") { it.name }
        val result = (executable as? Method)?.returnType?.name ?: "<init>"
        return "$feature|${executable.declaringClass.name}#${executable.name}($parameters):$result"
    }
}

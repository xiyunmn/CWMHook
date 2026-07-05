package com.xiyunmn.cwmhook.plan

import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

data class HookInstallEntry(
    val id: String,
    val install: (ClassLoader) -> Unit,
)

data class HookInstallPlan(
    val processName: String,
    val phase: String,
    val entries: List<HookInstallEntry>,
)

object HookInstaller {
    private const val TAG = "CWMHook.HookInstaller"

    fun install(plan: HookInstallPlan, classLoader: ClassLoader) {
        if (plan.entries.isEmpty()) {
            ModuleFileLogger.i(TAG, "Hook plan empty phase=${plan.phase} process=${plan.processName}")
            return
        }
        plan.entries.forEach { entry ->
            runCatching {
                ModuleFileLogger.i(TAG, "Installing ${entry.id} phase=${plan.phase} process=${plan.processName}")
                entry.install(classLoader)
            }.onFailure { throwable ->
                ModuleFileLogger.e(
                    TAG,
                    "Install failed id=${entry.id} phase=${plan.phase} process=${plan.processName}",
                    throwable,
                )
            }
        }
    }
}

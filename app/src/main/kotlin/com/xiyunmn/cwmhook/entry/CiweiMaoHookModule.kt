package com.xiyunmn.cwmhook.entry

import android.app.Application
import android.util.Log
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoPackages
import com.xiyunmn.cwmhook.plan.CiweiMaoHookPlanner
import com.xiyunmn.cwmhook.plan.HookInstaller
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class CiweiMaoHookModule : XposedModule() {
    private var processName: String = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        XposedCompat.attach(this)
        processName = param.processName
        log(Log.INFO, TAG, "Module loaded in process $processName")
        ModuleFileLogger.i(TAG, "Module loaded in process $processName")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != CiweiMaoPackages.NOVEL || !param.isFirstPackage) {
            return
        }

        log(
            Log.INFO,
            TAG,
            "Target package ready: package=${param.packageName}, " +
                "process=$processName, classLoader=${param.classLoader}"
        )
        ModuleFileLogger.i(
            TAG,
            "Target package ready: package=${param.packageName}, " +
                "process=$processName, classLoader=${param.classLoader}"
        )
        installStartupProbe(param.classLoader)
        HookInstaller.install(CiweiMaoHookPlanner.packageReadyPlan(this, processName), param.classLoader)
    }

    private fun installStartupProbe(classLoader: ClassLoader) {
        val onCreate = runCatching { Application::class.java.getDeclaredMethod("onCreate") }.getOrElse { throwable ->
            log(Log.ERROR, TAG, "Failed to install startup probe", throwable)
            ModuleFileLogger.e(TAG, "Failed to install startup probe", throwable)
            return
        }
        val hooked = XposedCompat.interceptProtective(this, onCreate, "$TAG.Application.onCreate") { chain ->
            val application = chain.thisObject as? Application
            if (application != null) {
                ModuleFileLogger.init(application, processName)
            }
            log(
                Log.INFO,
                TAG,
                "Application.onCreate: ${chain.thisObject.javaClass.name}"
            )
            ModuleFileLogger.i(TAG, "Application.onCreate begin: ${chain.thisObject.javaClass.name}")
            try {
                val result = chain.proceed()
                HookInstaller.install(
                    CiweiMaoHookPlanner.applicationReadyRetryPlan(
                        module = this,
                        processName = processName,
                        reason = "Application.onCreate",
                    ),
                    classLoader,
                )
                ModuleFileLogger.i(TAG, "Application.onCreate end: ${chain.thisObject.javaClass.name}")
                result
            } catch (throwable: Throwable) {
                ModuleFileLogger.e(TAG, "Application.onCreate failed: ${chain.thisObject.javaClass.name}", throwable)
                throw throwable
            }
        }
        if (!hooked) {
            log(Log.ERROR, TAG, "Failed to install startup probe")
            ModuleFileLogger.e(TAG, "Failed to install startup probe")
        }
    }

    private companion object {
        const val TAG = "CWMHook"
    }
}

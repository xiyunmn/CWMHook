package com.xiyunmn.cwmhook.entry

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.feature.startupprobe.NativeStartupProbeLoader
import com.xiyunmn.cwmhook.feature.startupprobe.StartupNetworkTaskProbe
import com.xiyunmn.cwmhook.feature.startupprobe.StartupTimelineProbe
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import com.xiyunmn.cwmhook.host.CiweiMaoPackages
import com.xiyunmn.cwmhook.plan.CiweiMaoHookPlanner
import com.xiyunmn.cwmhook.plan.HookInstaller
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class CiweiMaoHookModule : XposedModule() {
    private var processName: String = ""
    @Volatile
    private var applicationReadyRetried = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        XposedCompat.attach(this)
        processName = param.processName
        StartupTimelineProbe.mark("moduleLoaded", processName)
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
        StartupTimelineProbe.mark("packageReady", processName)
        NativeStartupProbeLoader.load()
        installStartupProbe(param.classLoader)
        HookInstaller.install(CiweiMaoHookPlanner.packageReadyPlan(this, processName), param.classLoader)
    }

    private fun installStartupProbe(classLoader: ClassLoader) {
        installApplicationAttachProbe()
        installApplicationOnCreateProbe(classLoader)
        installActivityStartupProbe()
    }

    private fun installApplicationAttachProbe() {
        val attach = runCatching {
            Application::class.java.getDeclaredMethod("attach", Context::class.java).also { it.isAccessible = true }
        }.getOrElse { throwable ->
            log(Log.ERROR, TAG, "Failed to install Application.attach probe", throwable)
            ModuleFileLogger.e(TAG, "Failed to install Application.attach probe", throwable)
            return
        }
        val hooked = XposedCompat.interceptProtective(this, attach, "$TAG.Application.attach") { chain ->
            val applicationName = chain.thisObject.javaClass.name
            val baseContext = chain.getArg(0) as? Context
            if (baseContext != null) {
                ModuleFileLogger.init(baseContext, processName)
                StartupTimelineProbe.configure(baseContext, "Application.attach:$applicationName")
                StartupNetworkTaskProbe.configure(baseContext)
            }
            StartupTimelineProbe.mark("Application.attach.begin", startupClassLabel(applicationName))
            val result = chain.proceed()
            StartupTimelineProbe.mark("Application.attach.end", startupClassLabel(applicationName))
            result
        }
        if (!hooked) {
            log(Log.ERROR, TAG, "Failed to install Application.attach probe")
            ModuleFileLogger.e(TAG, "Failed to install Application.attach probe")
        }
    }

    private fun installApplicationOnCreateProbe(classLoader: ClassLoader) {
        val onCreate = runCatching { Application::class.java.getDeclaredMethod("onCreate") }.getOrElse { throwable ->
            log(Log.ERROR, TAG, "Failed to install startup probe", throwable)
            ModuleFileLogger.e(TAG, "Failed to install startup probe", throwable)
            return
        }
        val hooked = XposedCompat.interceptProtective(this, onCreate, "$TAG.Application.onCreate") { chain ->
            val application = chain.thisObject as? Application
            val applicationName = chain.thisObject.javaClass.name
            if (application != null) {
                ModuleFileLogger.init(application, processName)
                StartupTimelineProbe.configure(application, "Application.onCreate:$applicationName")
                StartupNetworkTaskProbe.configure(application)
            }
            StartupTimelineProbe.mark("Application.onCreate.begin", startupClassLabel(applicationName))
            log(
                Log.INFO,
                TAG,
                "Application.onCreate: $applicationName"
            )
            ModuleFileLogger.i(TAG, "Application.onCreate begin: $applicationName")
            try {
                val result = chain.proceed()
                if (!applicationReadyRetried) {
                    applicationReadyRetried = true
                    HookInstaller.install(
                        CiweiMaoHookPlanner.applicationReadyRetryPlan(
                            module = this,
                            processName = processName,
                            reason = "Application.onCreate:$applicationName",
                        ),
                        classLoader,
                    )
                }
                ModuleFileLogger.i(TAG, "Application.onCreate end: $applicationName")
                StartupTimelineProbe.mark("Application.onCreate.end", startupClassLabel(applicationName))
                if (applicationName == CiweiMaoClasses.APP) {
                    StartupTimelineProbe.dumpOnce("realApplication.onCreate")
                }
                result
            } catch (throwable: Throwable) {
                ModuleFileLogger.e(TAG, "Application.onCreate failed: $applicationName", throwable)
                throw throwable
            }
        }
        if (!hooked) {
            log(Log.ERROR, TAG, "Failed to install startup probe")
            ModuleFileLogger.e(TAG, "Failed to install startup probe")
        }
    }

    private fun installActivityStartupProbe() {
        XposedCompat.hookAfter(
            this,
            Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java),
            "$TAG.Activity.onCreate.startupProbe",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val label = startupActivityLabel(activity.javaClass.name) ?: return@hookAfter
            StartupTimelineProbe.configure(activity, "Activity.onCreate:$label")
            StartupTimelineProbe.mark("Activity.onCreate", label)
            if (activity.javaClass.name == CiweiMaoClasses.MAIN_FRAME_ACTIVITY) {
                StartupTimelineProbe.dumpOnce("MainFrameActivity.onCreate")
            }
        }
        XposedCompat.hookAfter(
            this,
            Activity::class.java.getDeclaredMethod("onResume"),
            "$TAG.Activity.onResume.startupProbe",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val label = startupActivityLabel(activity.javaClass.name) ?: return@hookAfter
            StartupTimelineProbe.mark("Activity.onResume", label)
        }
        XposedCompat.hookAfter(
            this,
            Activity::class.java.getDeclaredMethod("onPostResume"),
            "$TAG.Activity.onPostResume.startupProbe",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val label = startupActivityLabel(activity.javaClass.name) ?: return@hookAfter
            StartupTimelineProbe.mark("Activity.onPostResume", label)
            if (activity.javaClass.name == CiweiMaoClasses.MAIN_FRAME_ACTIVITY) {
                StartupTimelineProbe.dumpOnce("MainFrameActivity.onPostResume")
            }
        }
    }

    private fun startupClassLabel(className: String): String {
        return when (className) {
            CiweiMaoClasses.STUB_APP -> "StubApp"
            CiweiMaoClasses.APP -> "App"
            else -> className.substringAfterLast('.')
        }
    }

    private fun startupActivityLabel(className: String): String? {
        return when (className) {
            CiweiMaoClasses.SPLASH_ACTIVITY -> "SplashActivity"
            CiweiMaoClasses.WELCOME_ACTIVITY -> "WelcomeActivity"
            CiweiMaoClasses.ADVERTISEMENT_ACTIVITY -> "AdvertisementActivity"
            CiweiMaoClasses.MAIN_FRAME_ACTIVITY -> "MainFrameActivity"
            else -> null
        }
    }

    private companion object {
        const val TAG = "CWMHook"
    }
}

package com.xiyunmn.cwmhook.feature.autosignin

import android.app.Activity
import android.widget.Toast
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import io.github.libxposed.api.XposedModule

object AutoSignInFeature {
    private const val TAG = "CWMHook.AutoSignInFeature"
    private const val AUTO_DELAY_MS = 1_200L

    private val hookInstaller = AutoSignInHookInstaller(::scheduleAutoSignIn)
    private var executor: AutoSignInExecutor? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        ensureExecutor(classLoader)
        hookInstaller.install(module)
        ModuleFileLogger.i(TAG, "Auto sign-in feature installed")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        ensureExecutor(classLoader)
        hookInstaller.install(module)
        ModuleFileLogger.i(TAG, "Auto sign-in retry checked: $reason")
    }

    fun prepareForHotReload(): Boolean {
        val current = executor ?: return true
        if (!current.shutdownIfIdle()) {
            ModuleFileLogger.w(TAG, "Hot reload rejected because auto sign-in is active")
            return false
        }
        executor = null
        return true
    }

    fun canHotReload(): Boolean = executor?.isIdle() != false

    fun triggerManual(activity: Activity) {
        val currentExecutor = executor
        if (currentExecutor == null) {
            Toast.makeText(activity, "自动签到尚未初始化", Toast.LENGTH_SHORT).show()
            ModuleFileLogger.w(TAG, "Manual sign-in skipped, executor unavailable")
            return
        }
        currentExecutor.triggerManual(activity)
    }

    private fun ensureExecutor(classLoader: ClassLoader) {
        if (executor == null) {
            executor = AutoSignInExecutor(classLoader)
        }
    }

    private fun scheduleAutoSignIn(activity: Activity, reason: String) {
        val currentExecutor = executor ?: return
        if (activity.isFinishing) {
            return
        }
        if (!activity.hasWindowFocus()) {
            ModuleFileLogger.throttled(
                key = "$TAG.waitFocus.${activity.javaClass.name}",
                intervalMs = 30_000L,
                priority = android.util.Log.INFO,
                tag = TAG,
                message = "Auto sign-in waits for window focus: reason=$reason, activity=${activity.javaClass.name}",
            )
            return
        }
        ModuleViewTaskRegistry.post(activity.window.decorView, AUTO_DELAY_MS) {
            if (!activity.isFinishing) {
                currentExecutor.tryAuto(activity, reason)
            }
        }
    }
}

package com.xiyunmn.cwmhook.feature.autosignin

import android.app.Activity
import android.widget.Toast
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.HostLoadActivityTracker
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap

object AutoSignInFeature {
    private const val TAG = "CWMHook.AutoSignInFeature"
    internal const val HOST_QUIET_PERIOD_MS = 600L
    private const val MIN_RETRY_DELAY_MS = 100L

    private val hookInstaller = AutoSignInHookInstaller(::scheduleAutoSignIn)
    private val pendingActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
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
        synchronized(pendingActivities) {
            pendingActivities.clear()
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
        if (
            executor == null ||
            activity.isFinishing || activity.isDestroyed ||
            activity.javaClass.name in startupActivities
        ) {
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
        val added = synchronized(pendingActivities) {
            pendingActivities.add(activity)
        }
        if (!added) {
            return
        }
        // Give a newly focused page a short settling window for its initial tasks.
        postWhenHostIsQuiet(activity, reason, maxOf(HOST_QUIET_PERIOD_MS, delayUntilHostIsReadyMs()))
    }

    private fun postWhenHostIsQuiet(activity: Activity, reason: String, delayMs: Long) {
        val posted = ModuleViewTaskRegistry.post(activity.window.decorView, delayMs.coerceAtLeast(MIN_RETRY_DELAY_MS)) {
            if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) {
                removePendingActivity(activity)
                return@post
            }
            val remainingDelayMs = delayUntilHostIsReadyMs()
            if (remainingDelayMs > 0L) {
                ModuleFileLogger.throttled(
                    key = "$TAG.yieldToHost",
                    intervalMs = 10_000L,
                    priority = android.util.Log.INFO,
                    tag = TAG,
                    message = "Auto sign-in yields to host loading: remaining=${remainingDelayMs}ms",
                )
                postWhenHostIsQuiet(activity, reason, remainingDelayMs)
                return@post
            }
            val current = executor
            if (current == null) {
                removePendingActivity(activity)
            } else {
                current.tryAuto(activity, reason) { deferred ->
                    if (deferred) {
                        postWhenHostIsQuiet(activity, reason, delayUntilHostIsReadyMs())
                    } else {
                        removePendingActivity(activity)
                    }
                }
            }
        }
        if (!posted) {
            removePendingActivity(activity)
        }
    }

    private fun delayUntilHostIsReadyMs(): Long {
        return HostLoadActivityTracker.remainingQuietDelayMs(
            quietPeriodMs = HOST_QUIET_PERIOD_MS,
        )
    }

    private val startupActivities = setOf(
        CiweiMaoClasses.SPLASH_ACTIVITY,
        CiweiMaoClasses.WELCOME_ACTIVITY,
        CiweiMaoClasses.ADVERTISEMENT_ACTIVITY,
    )

    private fun removePendingActivity(activity: Activity) {
        synchronized(pendingActivities) {
            pendingActivities.remove(activity)
        }
    }
}

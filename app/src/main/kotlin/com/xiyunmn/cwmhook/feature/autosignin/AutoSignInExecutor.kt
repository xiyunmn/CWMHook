package com.xiyunmn.cwmhook.feature.autosignin

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xiyunmn.cwmhook.config.autosignin.AutoSignInConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.core.runtime.HostLoadActivityTracker
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class AutoSignInExecutor(
    classLoader: ClassLoader,
) {
    private enum class Trigger {
        AUTO,
        MANUAL,
    }

    private val bridge = AutoSignInHostBridge(classLoader)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CWMHook-AutoSignIn").apply { isDaemon = true }
    }
    private val lock = Any()
    private var inFlight = false
    private var pendingWorkerTasks = 0
    private var shuttingDown = false

    fun tryAuto(activity: Activity, reason: String, onChecked: (Boolean) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) {
            onChecked(false)
            return
        }
        val appContext = appContext(activity)
        val activityRef = WeakReference(activity)
        val accepted = executeOnWorker("auto eligibility") {
            var deferred = false
            try {
                val config = AutoSignInConfigStore.readLocal(appContext)
                if (!config.enabled) {
                    return@executeOnWorker
                }
                val user = bridge.currentUser() ?: run {
                    ModuleFileLogger.throttled(
                        key = "$TAG.noUser",
                        intervalMs = 60_000L,
                        priority = android.util.Log.INFO,
                        tag = TAG,
                        message = "Auto sign-in skipped, user not logged in",
                    )
                    return@executeOnWorker
                }
                val today = AutoSignInConfigStore.today()
                if (AutoSignInConfigStore.hasAttemptedToday(appContext, user.readerId, today)) {
                    return@executeOnWorker
                }
                // Eligibility checks can involve disk IO. Recheck loading immediately before dispatch,
                // since another Tab may have started a request while this worker was queued.
                if (HostLoadActivityTracker.remainingQuietDelayMs(AutoSignInFeature.HOST_QUIET_PERIOD_MS) > 0L) {
                    deferred = true
                    return@executeOnWorker
                }
                request(activityRef, appContext, user, today, Trigger.AUTO, reason)
            } finally {
                mainHandler.post { onChecked(deferred) }
            }
        }
        if (!accepted) {
            onChecked(false)
        }
    }

    fun triggerManual(activity: Activity) {
        val appContext = appContext(activity)
        val activityRef = WeakReference(activity)
        val accepted = executeOnWorker("manual eligibility") {
            val user = bridge.currentUser() ?: run {
                toast(activityRef, appContext, "请先登录刺猬猫账号")
                return@executeOnWorker
            }
            request(
                activityRef,
                appContext,
                user,
                AutoSignInConfigStore.today(),
                Trigger.MANUAL,
                "manual",
            )
        }
        if (!accepted) {
            toast(activityRef, appContext, "自动签到正在重新加载，请稍后重试")
        }
    }

    private fun request(
        activityRef: WeakReference<Activity>,
        appContext: Context,
        user: AutoSignInHostBridge.HostUser,
        date: String,
        trigger: Trigger,
        reason: String,
    ) {
        if (!beginRequest()) {
            if (trigger == Trigger.MANUAL) {
                toast(activityRef, appContext, "签到请求进行中")
            }
            return
        }
        ModuleFileLogger.i(TAG, "Sign-in requested: trigger=$trigger, reason=$reason, reader=${user.readerId.masked()}")
        bridge.signIn(appContext, object : AutoSignInHostBridge.Callback {
            override fun onSuccess(result: AutoSignInHostBridge.HostResult) {
                dispatchResult("success") {
                    endRequest()
                    bridge.applyResultToHost(result)
                    val message = successMessage(trigger, result)
                    AutoSignInConfigStore.recordResult(appContext, user.readerId, date, true, message)
                    toast(activityRef, appContext, message)
                    ModuleFileLogger.i(TAG, "Sign-in success: trigger=$trigger, reader=${user.readerId.masked()}")
                }
            }

            override fun onFailure(message: String) {
                dispatchResult("failure") {
                    endRequest()
                    val cleanMessage = message.ifBlank { "服务端未返回失败原因" }
                    val toastMessage = failureMessage(trigger, cleanMessage)
                    if (trigger == Trigger.AUTO) {
                        AutoSignInConfigStore.recordResult(appContext, user.readerId, date, false, toastMessage)
                    }
                    toast(activityRef, appContext, toastMessage)
                    ModuleFileLogger.w(TAG, "Sign-in failed: trigger=$trigger, reader=${user.readerId.masked()}, message=$cleanMessage")
                }
            }

            override fun onNetworkUnavailable() {
                dispatchResult("network unavailable") {
                    endRequest()
                    val toastMessage = failureMessage(trigger, "网络异常，请稍后手动重试")
                    if (trigger == Trigger.AUTO) {
                        AutoSignInConfigStore.recordResult(appContext, user.readerId, date, false, toastMessage)
                    }
                    toast(activityRef, appContext, toastMessage)
                    ModuleFileLogger.w(TAG, "Sign-in network unavailable: trigger=$trigger, reader=${user.readerId.masked()}")
                }
            }
        })
    }

    private fun beginRequest(): Boolean {
        synchronized(lock) {
            if (inFlight) {
                return false
            }
            inFlight = true
            return true
        }
    }

    private fun endRequest() {
        synchronized(lock) {
            inFlight = false
        }
    }

    fun shutdownIfIdle(): Boolean {
        synchronized(lock) {
            if (inFlight || pendingWorkerTasks > 0) {
                return false
            }
            shuttingDown = true
        }
        mainHandler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        return true
    }

    fun isIdle(): Boolean = synchronized(lock) { !inFlight && pendingWorkerTasks == 0 }

    private fun successMessage(trigger: Trigger, result: AutoSignInHostBridge.HostResult): String {
        val prefix = if (trigger == Trigger.AUTO) "自动签到成功" else "签到成功"
        return prefix + result.reward.toToastSuffix()
    }

    private fun failureMessage(trigger: Trigger, message: String): String {
        val prefix = if (trigger == Trigger.AUTO) "自动签到失败" else "签到失败"
        return "$prefix：$message"
    }

    private fun toast(activityRef: WeakReference<Activity>, fallbackContext: Context, message: String) {
        mainHandler.post {
            val activity = activityRef.get()
            val liveActivity = activity?.takeUnless { it.isFinishing || it.isDestroyed }
            val context = liveActivity ?: fallbackContext
            val showToast = Runnable {
                runCatching {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }.onSuccess {
                    ModuleFileLogger.i(TAG, "Toast requested: activity=${activity?.javaClass?.name ?: "none"}")
                }.onFailure { throwable ->
                    ModuleFileLogger.w(TAG, "Toast request failed: activity=${activity?.javaClass?.name ?: "none"}", throwable)
                }
            }
            if (liveActivity == null) {
                showToast.run()
            } else {
                ModuleViewTaskRegistry.post(liveActivity.window.decorView) { showToast.run() }
            }
        }
    }

    private fun dispatchResult(label: String, action: () -> Unit) {
        if (!executeOnWorker("result $label", action)) {
            endRequest()
            ModuleFileLogger.w(TAG, "Sign-in result dropped during shutdown: $label")
        }
    }

    private fun executeOnWorker(label: String, action: () -> Unit): Boolean {
        synchronized(lock) {
            if (shuttingDown) {
                return false
            }
            pendingWorkerTasks += 1
        }
        return runCatching {
            worker.execute {
                try {
                    runCatching {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                    }
                    action()
                } catch (throwable: Throwable) {
                    ModuleFileLogger.e(TAG, "Auto sign-in worker failed: $label", throwable)
                } finally {
                    synchronized(lock) {
                        pendingWorkerTasks = (pendingWorkerTasks - 1).coerceAtLeast(0)
                    }
                }
            }
            true
        }.getOrElse { throwable ->
            synchronized(lock) {
                pendingWorkerTasks = (pendingWorkerTasks - 1).coerceAtLeast(0)
            }
            ModuleFileLogger.e(TAG, "Auto sign-in worker rejected: $label", throwable)
            false
        }
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }

    private fun String.masked(): String {
        return if (length <= 4) "****" else "****" + takeLast(4)
    }

    private companion object {
        const val TAG = "CWMHook.AutoSignIn"
    }
}

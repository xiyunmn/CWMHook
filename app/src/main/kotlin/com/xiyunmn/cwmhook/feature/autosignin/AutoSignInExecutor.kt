package com.xiyunmn.cwmhook.feature.autosignin

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xiyunmn.cwmhook.config.autosignin.AutoSignInConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

internal class AutoSignInExecutor(
    classLoader: ClassLoader,
) {
    private enum class Trigger {
        AUTO,
        MANUAL,
    }

    private val bridge = AutoSignInHostBridge(classLoader)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var inFlight = false

    fun tryAuto(activity: Activity, reason: String) {
        val appContext = appContext(activity)
        val config = AutoSignInConfigStore.readLocal(appContext)
        if (!config.enabled || activity.isFinishing) {
            return
        }
        val user = bridge.currentUser() ?: run {
            ModuleFileLogger.throttled(
                key = "$TAG.noUser",
                intervalMs = 60_000L,
                priority = android.util.Log.INFO,
                tag = TAG,
                message = "Auto sign-in skipped, user not logged in",
            )
            return
        }
        val today = AutoSignInConfigStore.today()
        if (AutoSignInConfigStore.hasAttemptedToday(appContext, user.readerId, today)) {
            return
        }
        request(activity, appContext, user, today, Trigger.AUTO, reason)
    }

    fun triggerManual(activity: Activity) {
        val appContext = appContext(activity)
        val user = bridge.currentUser() ?: run {
            toast(appContext, "请先登录刺猬猫账号")
            return
        }
        request(activity, appContext, user, AutoSignInConfigStore.today(), Trigger.MANUAL, "manual")
    }

    private fun request(
        activity: Activity,
        appContext: Context,
        user: AutoSignInHostBridge.HostUser,
        date: String,
        trigger: Trigger,
        reason: String,
    ) {
        if (!beginRequest()) {
            if (trigger == Trigger.MANUAL) {
                toast(appContext, "签到请求进行中")
            }
            return
        }
        ModuleFileLogger.i(TAG, "Sign-in requested: trigger=$trigger, reason=$reason, reader=${user.readerId.masked()}")
        bridge.signIn(activity, object : AutoSignInHostBridge.Callback {
            override fun onSuccess(result: AutoSignInHostBridge.HostResult) {
                endRequest()
                val message = successMessage(trigger, result)
                AutoSignInConfigStore.recordResult(appContext, user.readerId, date, true, message)
                toast(appContext, message)
                ModuleFileLogger.i(TAG, "Sign-in success: trigger=$trigger, reader=${user.readerId.masked()}")
            }

            override fun onFailure(message: String) {
                endRequest()
                val cleanMessage = message.ifBlank { "服务端未返回失败原因" }
                val toastMessage = failureMessage(trigger, cleanMessage)
                if (trigger == Trigger.AUTO) {
                    AutoSignInConfigStore.recordResult(appContext, user.readerId, date, false, toastMessage)
                }
                toast(appContext, toastMessage)
                ModuleFileLogger.w(TAG, "Sign-in failed: trigger=$trigger, reader=${user.readerId.masked()}, message=$cleanMessage")
            }

            override fun onNetworkUnavailable() {
                endRequest()
                val toastMessage = failureMessage(trigger, "网络异常，请稍后手动重试")
                if (trigger == Trigger.AUTO) {
                    AutoSignInConfigStore.recordResult(appContext, user.readerId, date, false, toastMessage)
                }
                toast(appContext, toastMessage)
                ModuleFileLogger.w(TAG, "Sign-in network unavailable: trigger=$trigger, reader=${user.readerId.masked()}")
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

    private fun successMessage(trigger: Trigger, result: AutoSignInHostBridge.HostResult): String {
        val prefix = if (trigger == Trigger.AUTO) "自动签到成功" else "签到成功"
        return prefix + result.reward.toToastSuffix()
    }

    private fun failureMessage(trigger: Trigger, message: String): String {
        val prefix = if (trigger == Trigger.AUTO) "自动签到失败" else "签到失败"
        return "$prefix：$message"
    }

    private fun toast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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

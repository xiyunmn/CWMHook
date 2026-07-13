package com.xiyunmn.cwmhook.ui.restart

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import android.widget.Toast
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import com.xiyunmn.cwmhook.host.CiweiMaoPackages

internal object SettingsHostRestarter {
    private const val TAG = "CWMHook.Restart"
    private const val HOST_RESTART_REQUEST_CODE = 0x43574d
    private const val TRAMPOLINE_READY_REQUEST_CODE = 0x43574e
    private const val HOST_KILL_AFTER_TRAMPOLINE_READY_DELAY_MS = 280L
    private const val TRAMPOLINE_READY_TIMEOUT_MS = 180_000L
    private const val ACTION_TRAMPOLINE_READY =
        "com.xiyunmn.cwmhook.action.HOST_RESTART_TRAMPOLINE_READY"

    fun restartHost(activity: Activity, beforeKill: () -> Unit) {
        if (activity.packageName != CiweiMaoPackages.NOVEL) {
            Toast.makeText(activity, "请在刺猬猫内重启宿主", Toast.LENGTH_SHORT).show()
            return
        }
        val restartPendingIntent = createHostRestartPendingIntent(activity)
        val readyPendingIntent = createTrampolineReadyPendingIntent(activity)
        val receiver = TrampolineReadyReceiver(activity, beforeKill)
        registerTrampolineReadyReceiver(activity, receiver)
        val started = runCatching {
            activity.startActivity(
                HostRestartTrampolineActivity.createIntent(
                    restartPendingIntent = restartPendingIntent,
                    readyPendingIntent = readyPendingIntent,
                ),
            )
        }.onFailure { throwable ->
            unregisterTrampolineReadyReceiver(activity, receiver)
            ModuleFileLogger.e(TAG, "Failed to start restart trampoline", throwable)
        }.isSuccess
        if (!started) {
            Toast.makeText(activity, "重启失败，请稍后重试", Toast.LENGTH_LONG).show()
            return
        }
        activity.window.decorView.postDelayed({
            if (receiver.markTimedOut()) {
                unregisterTrampolineReadyReceiver(activity, receiver)
                ModuleFileLogger.w(TAG, "Restart trampoline did not report ready before timeout")
            }
        }, TRAMPOLINE_READY_TIMEOUT_MS)
    }

    private fun createHostRestartPendingIntent(activity: Activity): PendingIntent {
        return PendingIntent.getActivity(
            activity,
            HOST_RESTART_REQUEST_CODE,
            hostLaunchIntent(),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            pendingIntentCreatorOptions(),
        )
    }

    private fun createTrampolineReadyPendingIntent(activity: Activity): PendingIntent {
        return PendingIntent.getBroadcast(
            activity,
            TRAMPOLINE_READY_REQUEST_CODE,
            Intent(ACTION_TRAMPOLINE_READY).setPackage(activity.packageName),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun registerTrampolineReadyReceiver(activity: Activity, receiver: BroadcastReceiver) {
        val filter = IntentFilter(ACTION_TRAMPOLINE_READY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterTrampolineReadyReceiver(activity: Activity, receiver: BroadcastReceiver) {
        runCatching { activity.unregisterReceiver(receiver) }
    }

    private fun hostLaunchIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(CiweiMaoPackages.NOVEL, CiweiMaoClasses.SPLASH_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun pendingIntentCreatorOptions(): android.os.Bundle {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentCreatorBackgroundActivityStartMode(backgroundActivityStartMode())
        }
        return options.toBundle()
    }

    @Suppress("DEPRECATION")
    private fun backgroundActivityStartMode(): Int {
        return if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
    }

    private class TrampolineReadyReceiver(
        private val activity: Activity,
        private val beforeKill: () -> Unit,
    ) : BroadcastReceiver() {
        private var handled = false

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_TRAMPOLINE_READY || !markHandled()) {
                return
            }
            unregisterTrampolineReadyReceiver(activity, this)
            ModuleFileLogger.i(TAG, "Restart trampoline ready; scheduling host process kill")
            activity.window.decorView.postDelayed({
                runCatching { beforeKill() }
                ModuleFileLogger.i(TAG, "Killing protected host process for restart")
                Process.killProcess(Process.myPid())
                System.exit(0)
            }, HOST_KILL_AFTER_TRAMPOLINE_READY_DELAY_MS)
        }

        fun markTimedOut(): Boolean = markHandled()

        private fun markHandled(): Boolean {
            if (handled) {
                return false
            }
            handled = true
            return true
        }
    }
}

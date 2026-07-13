package com.xiyunmn.cwmhook.ui.restart

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import com.xiyunmn.cwmhook.host.CiweiMaoPackages

class HostRestartTrampolineActivity : Activity() {
    private var restartPendingIntent: PendingIntent? = null
    private var readyPendingIntent: PendingIntent? = null
    private var relaunchScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restartPendingIntent = intent.pendingIntentExtra(EXTRA_HOST_RESTART_PENDING_INTENT)
        readyPendingIntent = intent.pendingIntentExtra(EXTRA_TRAMPOLINE_READY_PENDING_INTENT)
        window.setGravity(Gravity.TOP or Gravity.START)
        window.setLayout(1, 1)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setContentView(TextView(this).apply { text = "" })
        notifyHostReady()
        scheduleRelaunch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restartPendingIntent = intent.pendingIntentExtra(EXTRA_HOST_RESTART_PENDING_INTENT)
        readyPendingIntent = intent.pendingIntentExtra(EXTRA_TRAMPOLINE_READY_PENDING_INTENT)
        notifyHostReady()
        scheduleRelaunch()
    }

    private fun notifyHostReady() {
        runCatching {
            readyPendingIntent?.send()
        }.onFailure { throwable ->
            Log.w(TAG, "Restart trampoline ready callback failed", throwable)
        }
    }

    private fun scheduleRelaunch() {
        if (relaunchScheduled) {
            return
        }
        relaunchScheduled = true
        window.decorView.postDelayed({ relaunchHost() }, RELAUNCH_DELAY_MS)
    }

    private fun relaunchHost() {
        val sent = restartPendingIntent?.let { pendingIntent ->
            sendHostRestartPendingIntent(pendingIntent)
        } == true
        if (!sent) {
            startHostActivityDirectly()
        }
        window.decorView.postDelayed({ finishAndRemoveTask() }, FINISH_DELAY_MS)
    }

    private fun sendHostRestartPendingIntent(pendingIntent: PendingIntent): Boolean {
        return runCatching {
            pendingIntent.send(this, 0, null, null, null, null, pendingIntentSenderOptions())
            true
        }.onFailure { throwable ->
            Log.w(TAG, "PendingIntent host restart failed", throwable)
        }.getOrDefault(false)
    }

    private fun pendingIntentSenderOptions(): Bundle {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(backgroundActivityStartMode())
        }
        return options.toBundle()
    }

    private fun startHostActivityDirectly() {
        runCatching { startActivity(hostLaunchIntent()) }
            .onFailure { throwable -> Log.w(TAG, "Direct host restart failed", throwable) }
    }

    private fun hostLaunchIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(CiweiMaoPackages.NOVEL, CiweiMaoClasses.SPLASH_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun Intent.pendingIntentExtra(name: String): PendingIntent? {
        @Suppress("DEPRECATION")
        return getParcelableExtra(name)
    }

    companion object {
        private const val TAG = "CWMHook.Restart"
        private const val MODULE_PACKAGE = "com.xiyunmn.cwmhook"
        private const val EXTRA_HOST_RESTART_PENDING_INTENT =
            "com.xiyunmn.cwmhook.extra.HOST_RESTART_PENDING_INTENT"
        private const val EXTRA_TRAMPOLINE_READY_PENDING_INTENT =
            "com.xiyunmn.cwmhook.extra.TRAMPOLINE_READY_PENDING_INTENT"
        private const val RELAUNCH_DELAY_MS = 1_100L
        private const val FINISH_DELAY_MS = 900L

        fun createIntent(
            restartPendingIntent: PendingIntent,
            readyPendingIntent: PendingIntent,
        ): Intent {
            return Intent().apply {
                setClassName(MODULE_PACKAGE, HostRestartTrampolineActivity::class.java.name)
                putExtra(EXTRA_HOST_RESTART_PENDING_INTENT, restartPendingIntent)
                putExtra(EXTRA_TRAMPOLINE_READY_PENDING_INTENT, readyPendingIntent)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            }
        }

        private fun backgroundActivityStartMode(): Int {
            return if (Build.VERSION.SDK_INT >= 36) {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            } else {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
        }
    }
}

package com.xiyunmn.cwmhook.runtime

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.xiyunmn.cwmhook.app.ModuleSettingsFeature
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.HostProcessInspector
import com.xiyunmn.cwmhook.core.runtime.ModuleOwnedUiCleaner
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.feature.autosignin.AutoSignInFeature
import com.xiyunmn.cwmhook.feature.chapterbackup.ChapterBackupFeature
import com.xiyunmn.cwmhook.feature.rewardad.RewardAdSkipFeature
import com.xiyunmn.cwmhook.feature.startupprobe.StartupNetworkTaskProbe
import com.xiyunmn.cwmhook.feature.statusbar.ImmersiveStatusBarFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object HotReloadCoordinator {
    private const val TAG = "CWMHook.HotReload"
    private const val MAIN_CLEANUP_TIMEOUT_MS = 2_500L
    private const val MAIN_CLEANUP_RUNNING_TIMEOUT_MS = 30_000L

    fun prepare(processName: String): Bundle? {
        if (!AutoSignInFeature.canHotReload()) {
            ModuleFileLogger.w(TAG, "Hot reload rejected: auto sign-in request is active")
            return null
        }
        if (!ChapterBackupFeature.canHotReload()) {
            ModuleFileLogger.w(TAG, "Hot reload rejected: chapter export/download is active")
            return null
        }

        XposedCompat.beginHotReload()
        val cleaned = runOnMainSync {
            check(AutoSignInFeature.canHotReload())
            check(ChapterBackupFeature.canHotReload())
            check(AutoSignInFeature.prepareForHotReload())
            check(ChapterBackupFeature.prepareForHotReload())
            RewardAdSkipFeature.prepareForHotReload()
            ModuleViewTaskRegistry.cancelAll()
            ModuleSettingsFeature.prepareForHotReload()
            val activities = HostProcessInspector.activities()
            ImmersiveStatusBarFeature.prepareForHotReload(activities)
            activities.forEach { activity ->
                ModuleOwnedUiCleaner.clean(activity.window.decorView)
            }
        }
        if (!cleaned) {
            XposedCompat.cancelHotReload()
            ModuleFileLogger.w(TAG, "Hot reload rejected: old generation could not become idle")
            return null
        }

        StartupNetworkTaskProbe.prepareForHotReload()
        val state = Bundle().apply {
            putString("processName", processName)
            putString("packageName", HostProcessInspector.currentApplication()?.packageName)
            putLong("retiredAt", System.currentTimeMillis())
        }
        ModuleFileLogger.i(TAG, "Old module generation retired: process=$processName")
        ModuleFileLogger.shutdownForHotReload()
        return state
    }

    fun resolveHostClassLoader(): ClassLoader? {
        return HostProcessInspector.currentApplication()?.classLoader
            ?: Thread.currentThread().contextClassLoader
    }

    fun recreateForegroundActivity(reason: String) {
        Handler(Looper.getMainLooper()).post {
            val activity = HostProcessInspector.currentActivity() ?: return@post
            if (activity.isFinishing || activity.isDestroyed) {
                return@post
            }
            ModuleFileLogger.i(TAG, "Recreating foreground Activity after hot reload: reason=$reason activity=${activity.javaClass.name}")
            activity.recreate()
        }
    }

    private fun runOnMainSync(block: () -> Unit): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return runCatching(block).isSuccess
        }
        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)
        val started = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val cleanup = Runnable {
            started.set(true)
            success.set(runCatching(block)
                .onFailure { throwable -> ModuleFileLogger.e(TAG, "Main-thread hot reload cleanup failed", throwable) }
                .isSuccess)
            latch.countDown()
        }
        if (!handler.post(cleanup)) {
            return false
        }
        val completed = runCatching { latch.await(MAIN_CLEANUP_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            .getOrDefault(false)
        if (!completed && !started.get()) {
            handler.removeCallbacks(cleanup)
            ModuleFileLogger.w(TAG, "Hot reload cleanup timed out before main-thread execution")
            return false
        }
        if (!completed) {
            ModuleFileLogger.w(TAG, "Hot reload cleanup exceeded timeout after starting; waiting for completion")
            val finished = runCatching { latch.await(MAIN_CLEANUP_RUNNING_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
                .onFailure { throwable ->
                    Thread.currentThread().interrupt()
                    ModuleFileLogger.w(TAG, "Interrupted while waiting for main-thread hot reload cleanup", throwable)
                }
                .getOrDefault(false)
            if (!finished) {
                ModuleFileLogger.w(TAG, "Hot reload cleanup did not finish within the extended timeout")
                return false
            }
        }
        return latch.count == 0L && success.get()
    }
}

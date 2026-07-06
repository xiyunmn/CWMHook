package com.xiyunmn.cwmhook.app

import android.app.Activity
import android.content.Intent
import android.os.Process
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.xiyunmn.cwmhook.config.autosignin.AutoSignInConfig
import com.xiyunmn.cwmhook.config.autosignin.AutoSignInConfigStore
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfig
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfig
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfigStore
import com.xiyunmn.cwmhook.config.startupopt.StartupOptimizeConfig
import com.xiyunmn.cwmhook.config.startupopt.StartupOptimizeConfigStore
import com.xiyunmn.cwmhook.config.startuptab.StartupTabConfig
import com.xiyunmn.cwmhook.config.startuptab.StartupTabConfigStore
import com.xiyunmn.cwmhook.config.statusbar.StatusBarConfig
import com.xiyunmn.cwmhook.config.statusbar.StatusBarConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.feature.autosignin.AutoSignInFeature
import com.xiyunmn.cwmhook.feature.bottomtab.BottomTabFeature
import com.xiyunmn.cwmhook.feature.chapterbackup.ChapterBackupFeature
import com.xiyunmn.cwmhook.feature.readerfont.ReaderFontFeature
import com.xiyunmn.cwmhook.feature.settings.ModuleSettingsEntryResolver
import com.xiyunmn.cwmhook.feature.settings.ModuleSettingsHookInstaller
import com.xiyunmn.cwmhook.feature.statusbar.ImmersiveStatusBarFeature
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.settings.ModuleSettingsPage
import com.xiyunmn.cwmhook.ui.settings.ModuleSettingsPageWindow
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap

object ModuleSettingsFeature {
    private const val TAG = "CWMHook.ModuleSettings"

    private val entryResolver = ModuleSettingsEntryResolver()
    private val hookInstaller = ModuleSettingsHookInstaller(
        logTag = TAG,
        onBookShelfEntryReady = ::attachBookShelfLongPressEntry,
        onMainFrameActivityReady = ::onMainFrameActivityReady,
        onReaderActivityReady = ::onReaderActivityReady,
        onMainFrameActivitySaveState = ::onMainFrameActivitySaveState,
        onMainFrameActivityDestroy = ::onMainFrameActivityDestroy,
        onHostThemeChangeStarted = ModuleSettingsPageWindow::captureActiveForHostChange,
        onHostThemeChanged = ::onHostThemeChanged,
        isBackKey = ModuleSettingsPageWindow::isBackKey,
        hasPanel = ModuleSettingsPageWindow::hasPanel,
        closePanel = { activity, reason -> closeExistingSettings(activity, reason) },
    )
    private val attachedEntries = WeakHashMap<View, String>()

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookInstaller.install(module, classLoader)
        ModuleFileLogger.i(TAG, "Module settings feature install requested")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hookInstaller.retryDeferredHooks(module, classLoader, reason)
    }

    private fun attachBookShelfLongPressEntry(fragment: Any) {
        val anchor = entryResolver.bookShelfAnchor(fragment) ?: return
        attachLongPressEntry(anchor, "bookshelf:onCreateView") {
            entryResolver.findActivity(anchor.context) ?: entryResolver.fragmentActivity(fragment)
        }
    }

    private fun onMainFrameActivityReady(activity: Activity, reason: String) {
        restoreSettingsIfNeeded(activity, reason)
    }

    private fun onReaderActivityReady(activity: Activity, reason: String) {
        restoreSettingsIfNeeded(activity, reason)
        attachReaderEntry(activity, reason)
    }

    private fun attachReaderEntry(activity: Activity, reason: String) {
        val anchor = entryResolver.readerMoreAnchor(activity) ?: return
        attachLongPressEntry(anchor, "reader:$reason") { activity }
    }

    private fun onMainFrameActivitySaveState(activity: Activity, reason: String) {
        ModuleSettingsPageWindow.captureActiveForHostChange(reason)
    }

    private fun onMainFrameActivityDestroy(activity: Activity, reason: String) {
        ModuleSettingsPageWindow.handleHostActivityDestroy(activity, reason)
        setStatusBarOverlayVisible(activity, false)
    }

    private fun onHostThemeChanged(reason: String) {
        ModuleSettingsPageWindow.refreshActiveThemes(reason)
    }

    private fun restoreSettingsIfNeeded(activity: Activity, reason: String) {
        ModuleSettingsPageWindow.restoreIfNeeded(
            activity = activity,
            createPage = ::createPage,
            reason = reason,
            onShown = {
                setStatusBarOverlayVisible(it, true)
                ModuleFileLogger.i(TAG, "Module settings page restored/shown: ${it.javaClass.name}")
            },
            onReused = {
                setStatusBarOverlayVisible(it, true)
                ModuleFileLogger.i(TAG, "Module settings page restore reused: ${it.javaClass.name}")
            },
            onClosed = { closeReason -> onSettingsClosed(activity, closeReason) },
        )
    }

    private fun attachLongPressEntry(anchor: View, reason: String, activityProvider: () -> Activity?) {
        if (attachedEntries[anchor] == reason) {
            return
        }
        anchor.setOnLongClickListener { view ->
            val activity = entryResolver.findActivity(view.context) ?: activityProvider()
            if (activity == null || activity.isFinishing) {
                ModuleFileLogger.w(TAG, "Module settings requested without live Activity")
                return@setOnLongClickListener true
            }
            showSettings(activity)
            true
        }
        anchor.isLongClickable = true
        attachedEntries[anchor] = reason
        ModuleFileLogger.i(TAG, "Module settings long press entry attached: $reason")
    }

    private fun showSettings(activity: Activity) {
        ModuleSettingsPageWindow.show(
            activity = activity,
            createPage = ::createPage,
            restoreState = null,
            onShown = {
                setStatusBarOverlayVisible(it, true)
                ModuleFileLogger.i(TAG, "Module settings page shown: ${it.javaClass.name}")
            },
            onReused = {
                setStatusBarOverlayVisible(it, true)
                ModuleFileLogger.i(TAG, "Module settings page reused: ${it.javaClass.name}")
            },
            onClosed = { reason -> onSettingsClosed(activity, reason) },
        )
    }

    private fun createPage(activity: Activity, overlay: FrameLayout, theme: PanelTheme, restoreState: Any?): View {
        return ModuleSettingsPage(
            activity = activity,
            overlay = overlay,
            theme = theme,
            initialStatusBarConfig = StatusBarConfigStore.readLocal(activity),
            initialBottomTabConfig = BottomTabConfigStore.readLocal(activity),
            initialReaderFontConfig = ReaderFontConfigStore.readLocal(activity),
            initialAutoSignInConfig = AutoSignInConfigStore.readLocal(activity),
            initialStartupOptimizeConfig = StartupOptimizeConfigStore.readLocal(activity),
            initialStartupTabConfig = StartupTabConfigStore.readLocal(activity),
            initialChapterBackupConfig = ChapterBackupConfigStore.readLocal(activity),
            restoreState = restoreState as? ModuleSettingsPage.RestoreState,
            onManualAutoSignIn = {
                AutoSignInFeature.triggerManual(activity)
            },
            onImportReaderFonts = {
                ReaderFontFeature.startFontImport(activity)
            },
            onChooseChapterBackupDirectory = {
                ChapterBackupFeature.launchDirectoryPicker(activity)
            },
            onClearChapterBackupDirectory = {
                ChapterBackupFeature.clearExportDirectory(activity)
            },
            onExportCachedChapters = {
                ChapterBackupFeature.exportCachedBooks(activity)
            },
            onSave = { statusBarConfig,
                    bottomTabConfig,
                    readerFontConfig,
                    autoSignInConfig,
                    startupOptimizeConfig,
                    startupTabConfig,
                    chapterBackupConfig ->
                saveAllConfigs(
                    activity,
                    statusBarConfig,
                    bottomTabConfig,
                    readerFontConfig,
                    autoSignInConfig,
                    startupOptimizeConfig,
                    startupTabConfig,
                    chapterBackupConfig,
                )
            },
            onRestartHost = {
                restartHost(activity)
            },
            onClose = { reason ->
                ModuleSettingsPageWindow.close(overlay, reason) { closeReason -> onSettingsClosed(activity, closeReason) }
            },
        )
    }

    private fun saveAllConfigs(
        activity: Activity,
        statusBarConfig: StatusBarConfig,
        bottomTabConfig: BottomTabConfig,
        readerFontConfig: ReaderFontConfig,
        autoSignInConfig: AutoSignInConfig,
        startupOptimizeConfig: StartupOptimizeConfig,
        startupTabConfig: StartupTabConfig,
        chapterBackupConfig: ChapterBackupConfig,
    ) {
        val statusBarSaved = StatusBarConfigStore.writeLocal(activity, statusBarConfig)
        val bottomTabSaved = BottomTabConfigStore.writeLocal(activity, bottomTabConfig)
        val readerFontSaved = ReaderFontConfigStore.writeLocal(activity, readerFontConfig)
        val autoSignInSaved = AutoSignInConfigStore.writeLocal(activity, autoSignInConfig)
        val startupOptimizeSaved = StartupOptimizeConfigStore.writeLocal(activity, startupOptimizeConfig)
        val startupTabSaved = StartupTabConfigStore.writeLocal(activity, startupTabConfig)
        val chapterBackupSaved = ChapterBackupConfigStore.writeLocal(
            activity,
            chapterBackupConfig,
        )

        BottomTabFeature.applyRuntimeConfig(activity, bottomTabConfig, "module settings")

        val message = if (
            statusBarSaved &&
            bottomTabSaved &&
            readerFontSaved &&
            autoSignInSaved &&
            startupOptimizeSaved &&
            startupTabSaved &&
            chapterBackupSaved
        ) {
            "已保存，底栏已应用，启动相关设置下次启动生效"
        } else {
            "部分配置保存失败，请查看日志"
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun restartHost(activity: Activity) {
        val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        if (launchIntent == null) {
            Toast.makeText(activity, "无法获取宿主启动入口，请手动重启", Toast.LENGTH_LONG).show()
            ModuleFileLogger.w(TAG, "Host restart requested but launch intent is null")
            return
        }
        ModuleFileLogger.i(TAG, "Restart host requested from module settings")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(launchIntent)
        activity.window.decorView.postDelayed(
            {
                Process.killProcess(Process.myPid())
            },
            180L,
        )
    }

    private fun logSettingsClosed(reason: String) {
        ModuleFileLogger.i(TAG, "Module settings closed: $reason")
    }

    private fun closeExistingSettings(activity: Activity, reason: String): Boolean {
        val closed = ModuleSettingsPageWindow.closeExisting(activity, reason) { closeReason ->
            onSettingsClosed(activity, closeReason)
        }
        if (!closed) {
            setStatusBarOverlayVisible(activity, false)
        }
        return closed
    }

    private fun onSettingsClosed(activity: Activity, reason: String) {
        setStatusBarOverlayVisible(activity, false)
        logSettingsClosed(reason)
    }

    private fun setStatusBarOverlayVisible(activity: Activity, visible: Boolean) {
        ImmersiveStatusBarFeature.setTransientOverlayVisible(activity, visible)
    }
}

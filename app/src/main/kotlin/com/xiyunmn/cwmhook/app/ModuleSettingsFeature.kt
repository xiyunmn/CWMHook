package com.xiyunmn.cwmhook.app

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.xiyunmn.cwmhook.config.autosignin.AutoSignInConfig
import com.xiyunmn.cwmhook.config.autosignin.AutoSignInConfigStore
import com.xiyunmn.cwmhook.config.bookshelf.BookshelfConfig
import com.xiyunmn.cwmhook.config.bookshelf.BookshelfConfigStore
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfig
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.config.debug.DebugConfig
import com.xiyunmn.cwmhook.config.debug.DebugConfigStore
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfig
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfigStore
import com.xiyunmn.cwmhook.config.rewardad.RewardAdSkipConfig
import com.xiyunmn.cwmhook.config.rewardad.RewardAdSkipConfigStore
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
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.restart.SettingsHostRestarter
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

    fun prepareForHotReload() {
        ModuleSettingsPageWindow.detachAll("hot reload")
        attachedEntries.keys.toList().forEach { anchor ->
            anchor.setOnLongClickListener(null)
            anchor.isLongClickable = false
        }
        attachedEntries.clear()
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
                ModuleFileLogger.i(TAG, "Module settings page restored/shown: ${it.javaClass.name}")
            },
            onReused = {
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
                ModuleFileLogger.i(TAG, "Module settings page shown: ${it.javaClass.name}")
            },
            onReused = {
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
            initialBookshelfConfig = BookshelfConfigStore.readLocal(activity),
            initialBottomTabConfig = BottomTabConfigStore.readLocal(activity),
            initialReaderFontConfig = ReaderFontConfigStore.readLocal(activity),
            initialAutoSignInConfig = AutoSignInConfigStore.readLocal(activity),
            initialStartupOptimizeConfig = StartupOptimizeConfigStore.readLocal(activity),
            initialStartupTabConfig = StartupTabConfigStore.readLocal(activity),
            initialRewardAdSkipConfig = RewardAdSkipConfigStore.readLocal(activity),
            initialChapterBackupConfig = ChapterBackupConfigStore.readLocal(activity),
            initialDebugConfig = DebugConfigStore.readLocal(activity),
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
                    bookshelfConfig,
                    bottomTabConfig,
                    readerFontConfig,
                    autoSignInConfig,
                    startupOptimizeConfig,
                    startupTabConfig,
                    rewardAdSkipConfig,
                    chapterBackupConfig,
                    debugConfig,
                    showToast ->
                saveAllConfigs(
                    activity,
                    statusBarConfig,
                    bookshelfConfig,
                    bottomTabConfig,
                    readerFontConfig,
                    autoSignInConfig,
                    startupOptimizeConfig,
                    startupTabConfig,
                    rewardAdSkipConfig,
                    chapterBackupConfig,
                    debugConfig,
                    showToast,
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
        bookshelfConfig: BookshelfConfig,
        bottomTabConfig: BottomTabConfig,
        readerFontConfig: ReaderFontConfig,
        autoSignInConfig: AutoSignInConfig,
        startupOptimizeConfig: StartupOptimizeConfig,
        startupTabConfig: StartupTabConfig,
        rewardAdSkipConfig: RewardAdSkipConfig,
        chapterBackupConfig: ChapterBackupConfig,
        debugConfig: DebugConfig,
        showToast: Boolean = true,
    ) {
        val debugSaved = DebugConfigStore.writeLocal(activity, debugConfig)
        if (debugSaved) {
            ModuleFileLogger.setDetailedFileLoggingEnabled(debugConfig.detailedFileLogEnabled)
        }
        val statusBarSaved = StatusBarConfigStore.writeLocal(activity, statusBarConfig)
        val bookshelfSaved = BookshelfConfigStore.writeLocal(activity, bookshelfConfig)
        val bottomTabSaved = BottomTabConfigStore.writeLocal(activity, bottomTabConfig)
        val readerFontSaved = ReaderFontConfigStore.writeLocal(activity, readerFontConfig)
        val autoSignInSaved = AutoSignInConfigStore.writeLocal(activity, autoSignInConfig)
        val startupOptimizeSaved = StartupOptimizeConfigStore.writeLocal(activity, startupOptimizeConfig)
        val startupTabSaved = StartupTabConfigStore.writeLocal(activity, startupTabConfig)
        val rewardAdSkipSaved = RewardAdSkipConfigStore.writeLocal(activity, rewardAdSkipConfig)
        val chapterBackupSaved = ChapterBackupConfigStore.writeLocal(
            activity,
            chapterBackupConfig,
        )

        BottomTabFeature.applyRuntimeConfig(activity, bottomTabConfig, "module settings")

        val message = if (
            statusBarSaved &&
            bookshelfSaved &&
            bottomTabSaved &&
            readerFontSaved &&
            autoSignInSaved &&
            startupOptimizeSaved &&
            startupTabSaved &&
            rewardAdSkipSaved &&
            chapterBackupSaved &&
            debugSaved
        ) {
            "已保存，底栏已应用，启动相关设置下次启动生效"
        } else {
            "部分配置保存失败，请查看日志"
        }
        if (showToast) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun restartHost(activity: Activity) {
        SettingsHostRestarter.restartHost(activity) {
            ModuleFileLogger.i(TAG, "Restart host requested from module settings")
        }
    }

    private fun logSettingsClosed(reason: String) {
        ModuleFileLogger.i(TAG, "Module settings closed: $reason")
    }

    private fun closeExistingSettings(activity: Activity, reason: String): Boolean {
        val closed = ModuleSettingsPageWindow.closeExisting(activity, reason) { closeReason ->
            onSettingsClosed(activity, closeReason)
        }
        return closed
    }

    private fun onSettingsClosed(activity: Activity, reason: String) {
        logSettingsClosed(reason)
    }
}

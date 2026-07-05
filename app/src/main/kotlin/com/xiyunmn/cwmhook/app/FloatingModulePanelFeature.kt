package com.xiyunmn.cwmhook.app

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfig
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfigStore
import com.xiyunmn.cwmhook.config.statusbar.StatusBarConfig
import com.xiyunmn.cwmhook.config.statusbar.StatusBarConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.feature.bottomtab.BottomTabFeature
import com.xiyunmn.cwmhook.feature.panel.FloatingPanelEntryResolver
import com.xiyunmn.cwmhook.feature.panel.FloatingPanelHookInstaller
import com.xiyunmn.cwmhook.feature.statusbar.ImmersiveStatusBarFeature
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.panel.FloatingPanelWindow
import com.xiyunmn.cwmhook.ui.panel.ModuleSettingsPanel
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap

object FloatingModulePanelFeature {
    private const val TAG = "CWMHook.FloatingPanel"

    private val entryResolver = FloatingPanelEntryResolver()
    private val hookInstaller = FloatingPanelHookInstaller(
        logTag = TAG,
        onFragmentEntryReady = ::attachLongPressEntry,
        onMainFrameActivityReady = ::attachMainFrameEntry,
        isBackKey = FloatingPanelWindow::isBackKey,
        hasPanel = FloatingPanelWindow::hasPanel,
        closePanel = { activity, reason -> closeExistingPanel(activity, reason) },
    )
    private val attachedEntries = WeakHashMap<View, String>()

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookInstaller.install(module, classLoader)
        ModuleFileLogger.i(TAG, "Floating module panel feature install requested")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hookInstaller.retryDeferredHooks(module, classLoader, reason)
    }

    private fun attachLongPressEntry(fragment: Any) {
        val anchor = entryResolver.fragmentAnchor(fragment) ?: return
        attachLongPressEntry(anchor, "fragment:initView") {
            entryResolver.findActivity(anchor.context) ?: entryResolver.fragmentActivity(fragment)
        }
    }

    private fun attachMainFrameEntry(activity: Activity, reason: String) {
        val anchor = entryResolver.mainFrameAnchor(activity) ?: return
        attachLongPressEntry(anchor, reason) { activity }
    }

    private fun attachLongPressEntry(anchor: View, reason: String, activityProvider: () -> Activity?) {
        if (attachedEntries[anchor] == reason) {
            return
        }
        anchor.setOnLongClickListener { view ->
            val activity = entryResolver.findActivity(view.context) ?: activityProvider()
            if (activity == null || activity.isFinishing) {
                ModuleFileLogger.w(TAG, "Floating panel requested without live Activity")
                return@setOnLongClickListener true
            }
            showPanel(activity)
            true
        }
        anchor.isLongClickable = true
        attachedEntries[anchor] = reason
        ModuleFileLogger.i(TAG, "Floating panel long press entry attached: $reason")
    }

    private fun showPanel(activity: Activity) {
        FloatingPanelWindow.show(
            activity = activity,
            createPanel = ::createPanel,
            onShown = {
                setStatusBarOverlayVisible(it, true)
                ModuleFileLogger.i(TAG, "Floating panel shown: ${it.javaClass.name}")
            },
            onReused = {
                setStatusBarOverlayVisible(it, true)
                ModuleFileLogger.i(TAG, "Floating panel reused: ${it.javaClass.name}")
            },
            onClosed = { reason -> onPanelClosed(activity, reason) },
        )
    }

    private fun createPanel(activity: Activity, overlay: FrameLayout, theme: PanelTheme): View {
        return ModuleSettingsPanel(
            activity = activity,
            theme = theme,
            initialStatusBarConfig = StatusBarConfigStore.readLocal(activity),
            initialBottomTabConfig = BottomTabConfigStore.readLocal(activity),
            initialReaderFontConfig = ReaderFontConfigStore.readLocal(activity),
            onClearStatusBarCache = {
                ImmersiveStatusBarFeature.clearColorCache(activity)
            },
            onReapplyStatusBar = {
                ImmersiveStatusBarFeature.reapplyForegroundWindow("manual")
            },
            onSave = { statusBarConfig, bottomTabConfig, readerFontConfig ->
                saveAllConfigs(activity, statusBarConfig, bottomTabConfig, readerFontConfig)
            },
            onClose = {
                FloatingPanelWindow.close(overlay, "save") { reason -> onPanelClosed(activity, reason) }
            },
        ).create()
    }

    private fun saveAllConfigs(
        activity: Activity,
        statusBarConfig: StatusBarConfig,
        bottomTabConfig: BottomTabConfig,
        readerFontConfig: ReaderFontConfig,
    ) {
        val statusBarSaved = StatusBarConfigStore.writeLocal(activity, statusBarConfig)
        val bottomTabSaved = BottomTabConfigStore.writeLocal(activity, bottomTabConfig)
        val readerFontSaved = ReaderFontConfigStore.writeLocal(activity, readerFontConfig)

        BottomTabFeature.applyRuntimeConfig(activity, bottomTabConfig, "floating panel")

        val message = if (statusBarSaved && bottomTabSaved && readerFontSaved) {
            "已保存，底栏已应用"
        } else {
            "部分配置保存失败，请查看日志"
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun logPanelClosed(reason: String) {
        ModuleFileLogger.i(TAG, "Floating panel closed: $reason")
    }

    private fun closeExistingPanel(activity: Activity, reason: String): Boolean {
        val closed = FloatingPanelWindow.closeExisting(activity, reason) { closeReason ->
            onPanelClosed(activity, closeReason)
        }
        if (!closed) {
            setStatusBarOverlayVisible(activity, false)
        }
        return closed
    }

    private fun onPanelClosed(activity: Activity, reason: String) {
        setStatusBarOverlayVisible(activity, false)
        logPanelClosed(reason)
    }

    private fun setStatusBarOverlayVisible(activity: Activity, visible: Boolean) {
        ImmersiveStatusBarFeature.setTransientOverlayVisible(activity, visible)
    }
}

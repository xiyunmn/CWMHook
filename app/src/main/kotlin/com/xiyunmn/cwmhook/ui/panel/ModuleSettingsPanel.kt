package com.xiyunmn.cwmhook.ui.panel

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.cwmhook.config.autosignin.AutoSignInConfig
import com.xiyunmn.cwmhook.config.autosignin.AutoSignInConfigStore
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfig
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfig
import com.xiyunmn.cwmhook.config.startuptab.StartupTabConfig
import com.xiyunmn.cwmhook.config.startuptab.StartupTabConfigStore
import com.xiyunmn.cwmhook.config.statusbar.StatusBarConfig
import com.xiyunmn.cwmhook.feature.chapterbackup.ChapterBackupDestination
import com.xiyunmn.cwmhook.ui.autosignin.AutoSignInSettingsSection
import com.xiyunmn.cwmhook.ui.bottomtab.BottomTabEditorPanel
import com.xiyunmn.cwmhook.ui.chapterbackup.ChapterBackupSettingsSection
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.roundRect
import com.xiyunmn.cwmhook.ui.readerfont.ReaderFontSettingsSection
import com.xiyunmn.cwmhook.ui.startuptab.StartupTabSettingsSection
import com.xiyunmn.cwmhook.ui.statusbar.StatusBarSettingsSection

internal class ModuleSettingsPanel(
    private val activity: Activity,
    private val theme: PanelTheme,
    private val initialStatusBarConfig: StatusBarConfig,
    private val initialBottomTabConfig: BottomTabConfig,
    private val initialReaderFontConfig: ReaderFontConfig,
    private val initialAutoSignInConfig: AutoSignInConfig,
    private val initialStartupTabConfig: StartupTabConfig,
    private val initialChapterBackupConfig: ChapterBackupConfig,
    private val onClearStatusBarCache: () -> Boolean,
    private val onReapplyStatusBar: () -> Unit,
    private val onManualAutoSignIn: () -> Unit,
    private val onChooseChapterBackupDirectory: () -> Unit,
    private val onClearChapterBackupDirectory: () -> Boolean,
    private val onExportCachedChapters: () -> Unit,
    private val onSave: (StatusBarConfig, BottomTabConfig, ReaderFontConfig, AutoSignInConfig, StartupTabConfig, ChapterBackupConfig) -> Unit,
    private val onClose: () -> Unit,
) {
    private var statusBarConfig = initialStatusBarConfig
    private var bottomTabConfig = initialBottomTabConfig
    private var readerFontConfig = initialReaderFontConfig
    private var autoSignInConfig = initialAutoSignInConfig
    private var startupTabConfig = initialStartupTabConfig
    private var chapterBackupConfig = initialChapterBackupConfig
    private var statusBarExpanded = false
    private var autoSignInExpanded = false
    private var readerFontExpanded = false
    private var startupTabExpanded = false
    private var chapterBackupExpanded = false
    private var bottomTabExpanded = false

    private val bottomTabEditorPanel = BottomTabEditorPanel(
        activity = activity,
        theme = theme,
        initialConfig = initialBottomTabConfig,
        onSave = { config -> bottomTabConfig = config },
    )

    fun create(): LinearLayout {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            elevation = dp(activity, 10).toFloat()
            background = roundRect(theme.panelBackground, dp(activity, 10).toFloat())
        }

        fun renderPanel() {
            panel.removeAllViews()
            panel.addView(createHeader(panel), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 58)))
            panel.addView(createSeparator())

            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 14))
            }
            addStatusBarSection(content, ::renderPanel)
            addAutoSignInSection(content, ::renderPanel)
            addChapterBackupSection(content, ::renderPanel)
            addReaderFontSection(content, ::renderPanel)
            addStartupTabSection(content, ::renderPanel)
            addBottomTabSection(content, ::renderPanel)

            val scrollView = ScrollView(activity).apply {
                isFillViewport = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            panel.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            panel.addView(createSeparator())
            panel.addView(
                bottomTabEditorPanel.createFooter(
                    onResetClick = { renderPanel() },
                    onSaveClick = {
                        syncChapterBackupPathFromStore()
                        onSave(
                            statusBarConfig,
                            bottomTabConfig,
                            readerFontConfig,
                            autoSignInConfig,
                            startupTabConfig,
                            chapterBackupConfig,
                        )
                        onClose()
                    },
                ),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 54)),
            )
        }

        renderPanel()
        return panel
    }

    private fun addStatusBarSection(content: LinearLayout, renderPanel: () -> Unit) {
        val statusBarSection = StatusBarSettingsSection(activity, theme)
        content.addView(
            statusBarSection.createView(
                StatusBarSettingsSection.UiState(
                    enabled = statusBarConfig.enabled,
                    expanded = statusBarExpanded,
                ),
                object : StatusBarSettingsSection.Callbacks {
                    override fun onEnabledChanged(enabled: Boolean) {
                        statusBarConfig = statusBarConfig.copy(enabled = enabled)
                        renderPanel()
                    }

                    override fun onClearCache() {
                        val success = onClearStatusBarCache()
                        Toast.makeText(
                            activity,
                            if (success) "缓存已清除" else "清除失败，请查看日志",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }

                    override fun onReapply() {
                        onReapplyStatusBar()
                        Toast.makeText(activity, "已重新应用当前页面", Toast.LENGTH_SHORT).show()
                    }

                    override fun onExpandedChanged(expanded: Boolean) {
                        statusBarExpanded = expanded
                        renderPanel()
                    }
                },
            ),
            sectionParams(),
        )
    }

    private fun addAutoSignInSection(content: LinearLayout, renderPanel: () -> Unit) {
        val section = AutoSignInSettingsSection(activity, theme)
        content.addView(
            section.createView(
                AutoSignInSettingsSection.UiState(
                    enabled = autoSignInConfig.enabled,
                    expanded = autoSignInExpanded,
                ),
                object : AutoSignInSettingsSection.Callbacks {
                    override fun onEnabledChanged(enabled: Boolean) {
                        autoSignInConfig = autoSignInConfig.copy(
                            enabled = enabled,
                            version = AutoSignInConfigStore.nextVersion(autoSignInConfig),
                        )
                        renderPanel()
                    }

                    override fun onManualSignIn() {
                        onManualAutoSignIn()
                    }

                    override fun onExpandedChanged(expanded: Boolean) {
                        autoSignInExpanded = expanded
                        renderPanel()
                    }
                },
            ),
            sectionParams(),
        )
    }

    private fun addReaderFontSection(content: LinearLayout, renderPanel: () -> Unit) {
        val section = ReaderFontSettingsSection(activity, theme)
        content.addView(
            section.createView(
                ReaderFontSettingsSection.UiState(
                    enabled = readerFontConfig.enabled,
                    expanded = readerFontExpanded,
                ),
                object : ReaderFontSettingsSection.Callbacks {
                    override fun onEnabledChanged(enabled: Boolean) {
                        readerFontConfig = readerFontConfig.copy(
                            enabled = enabled,
                            version = if (readerFontConfig.version == Int.MAX_VALUE) 1 else readerFontConfig.version + 1,
                        )
                        renderPanel()
                    }

                    override fun onExpandedChanged(expanded: Boolean) {
                        readerFontExpanded = expanded
                        renderPanel()
                    }
                },
            ),
            sectionParams(),
        )
    }

    private fun addChapterBackupSection(content: LinearLayout, renderPanel: () -> Unit) {
        val section = ChapterBackupSettingsSection(activity, theme)
        content.addView(
            section.createView(
                ChapterBackupSettingsSection.UiState(
                    enabled = chapterBackupConfig.enabled,
                    expanded = chapterBackupExpanded,
                    exportPathLabel = chapterBackupPathLabel(),
                ),
                object : ChapterBackupSettingsSection.Callbacks {
                    override fun onEnabledChanged(enabled: Boolean) {
                        chapterBackupConfig = chapterBackupConfig.copy(
                            enabled = enabled,
                            version = ChapterBackupConfigStore.nextVersion(chapterBackupConfig),
                        )
                        renderPanel()
                    }

                    override fun onChooseDirectory() {
                        onChooseChapterBackupDirectory()
                        scheduleChapterBackupPathRefresh(renderPanel)
                    }

                    override fun onClearDirectory() {
                        if (onClearChapterBackupDirectory()) {
                            chapterBackupConfig = chapterBackupConfig.copy(
                                exportTreeUri = null,
                                version = ChapterBackupConfigStore.nextVersion(chapterBackupConfig),
                            )
                            Toast.makeText(activity, "导出路径已清除", Toast.LENGTH_SHORT).show()
                            renderPanel()
                        } else {
                            Toast.makeText(activity, "清除失败，请查看日志", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onExportCached() {
                        if (!chapterBackupConfig.enabled) {
                            Toast.makeText(activity, "请先启用个人章节导出", Toast.LENGTH_SHORT).show()
                            return
                        }
                        syncChapterBackupPathFromStore()
                        ChapterBackupConfigStore.writeLocal(
                            activity,
                            chapterBackupConfig,
                        )
                        onExportCachedChapters()
                    }

                    override fun onExpandedChanged(expanded: Boolean) {
                        chapterBackupExpanded = expanded
                        renderPanel()
                    }
                },
            ),
            sectionParams(),
        )
    }

    private fun chapterBackupPathLabel(): String {
        return ChapterBackupDestination(activity, chapterBackupConfig.exportTreeUri, CHAPTER_EXPORT_LOG_TAG)
            .exportDirectoryLabel()
    }

    private fun syncChapterBackupPathFromStore(): Boolean {
        val persisted = ChapterBackupConfigStore.readLocal(activity)
        if (persisted.exportTreeUri == chapterBackupConfig.exportTreeUri) {
            return false
        }
        chapterBackupConfig = chapterBackupConfig.copy(
            exportTreeUri = persisted.exportTreeUri,
            version = maxOf(chapterBackupConfig.version, persisted.version),
        )
        return true
    }

    private fun scheduleChapterBackupPathRefresh(renderPanel: () -> Unit) {
        listOf(600L, 1500L, 3000L, 6000L, 12000L, 24000L, 45000L).forEach { delay ->
            activity.window.decorView.postDelayed(
                {
                    if (syncChapterBackupPathFromStore()) {
                        renderPanel()
                    }
                },
                delay,
            )
        }
    }

    private fun addBottomTabSection(content: LinearLayout, renderPanel: () -> Unit) {
        content.addView(
            bottomTabEditorPanel.createContent(
                expanded = bottomTabExpanded,
                onExpandedChanged = { expanded ->
                    bottomTabExpanded = expanded
                    renderPanel()
                },
            ),
        )
    }

    private fun addStartupTabSection(content: LinearLayout, renderPanel: () -> Unit) {
        val section = StartupTabSettingsSection(activity, theme)
        content.addView(
            section.createView(
                StartupTabSettingsSection.UiState(
                    enabled = startupTabConfig.enabled,
                    expanded = startupTabExpanded,
                    tabKey = startupTabConfig.tabKey,
                ),
                object : StartupTabSettingsSection.Callbacks {
                    override fun onEnabledChanged(enabled: Boolean) {
                        startupTabConfig = startupTabConfig.copy(
                            enabled = enabled,
                            version = StartupTabConfigStore.nextVersion(startupTabConfig),
                        )
                        renderPanel()
                    }

                    override fun onTabChanged(tabKey: String) {
                        startupTabConfig = startupTabConfig.copy(
                            tabKey = tabKey,
                            version = StartupTabConfigStore.nextVersion(startupTabConfig),
                        )
                        renderPanel()
                    }

                    override fun onExpandedChanged(expanded: Boolean) {
                        startupTabExpanded = expanded
                        renderPanel()
                    }
                },
            ),
            sectionParams(),
        )
    }

    private fun createHeader(panel: View): LinearLayout {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 18), 0, dp(activity, 18), 0)
            setOnTouchListener(FloatingPanelDragController(panel))
        }
        header.addView(
            TextView(activity).apply {
                text = "模块设置"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
                gravity = Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        return header
    }

    private fun createSeparator(): View {
        return View(activity).apply {
            setBackgroundColor(theme.separator)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        }
    }

    private fun sectionParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(activity, 16)
        }
    }

    private companion object {
        const val CHAPTER_EXPORT_LOG_TAG = "CWMHook.ChapterExport"
    }
}

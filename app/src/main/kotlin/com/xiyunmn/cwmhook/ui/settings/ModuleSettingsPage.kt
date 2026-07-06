package com.xiyunmn.cwmhook.ui.settings

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import com.xiyunmn.cwmhook.feature.chapterbackup.ChapterBackupDestination
import com.xiyunmn.cwmhook.ui.bottomtab.BottomTabPanelState
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.icons.IconType
import java.io.File

internal class ModuleSettingsPage(
    private val activity: Activity,
    private val overlay: FrameLayout,
    private val theme: PanelTheme,
    initialStatusBarConfig: StatusBarConfig,
    initialBottomTabConfig: BottomTabConfig,
    initialReaderFontConfig: ReaderFontConfig,
    initialAutoSignInConfig: AutoSignInConfig,
    initialStartupOptimizeConfig: StartupOptimizeConfig,
    initialStartupTabConfig: StartupTabConfig,
    initialChapterBackupConfig: ChapterBackupConfig,
    restoreState: RestoreState? = null,
    private val onManualAutoSignIn: () -> Unit,
    private val onImportReaderFonts: () -> Unit,
    private val onChooseChapterBackupDirectory: () -> Unit,
    private val onClearChapterBackupDirectory: () -> Boolean,
    private val onExportCachedChapters: () -> Unit,
    private val onSave: (
        StatusBarConfig,
        BottomTabConfig,
        ReaderFontConfig,
        AutoSignInConfig,
        StartupOptimizeConfig,
        StartupTabConfig,
        ChapterBackupConfig,
    ) -> Unit,
    private val onRestartHost: () -> Unit,
    private val onClose: (String) -> Unit,
) : LinearLayout(activity), ModuleSettingsPageWindow.RestorablePage {
    private var statusBarConfig = restoreState?.statusBarConfig ?: initialStatusBarConfig
    private var bottomTabConfig = restoreState?.bottomTabConfig ?: initialBottomTabConfig
    private var readerFontConfig = restoreState?.readerFontConfig ?: initialReaderFontConfig
    private var autoSignInConfig = restoreState?.autoSignInConfig ?: initialAutoSignInConfig
    private var startupOptimizeConfig = restoreState?.startupOptimizeConfig ?: initialStartupOptimizeConfig
    private var startupTabConfig = restoreState?.startupTabConfig ?: initialStartupTabConfig
    private var chapterBackupConfig = restoreState?.chapterBackupConfig ?: initialChapterBackupConfig
    private var bottomTabState = BottomTabPanelState.from(bottomTabConfig)
    private var currentPage = restoreState?.pageName?.toPage() ?: Page.Overview
    private var actionRowIndex = 0

    private lateinit var titleText: TextView
    private lateinit var content: LinearLayout
    private lateinit var rows: ModuleSettingsRows

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(theme.panelBackground)
        buildShell()
        render(currentPage)
    }

    override fun handleBack(): Boolean {
        if (currentPage == Page.ReaderFontManager) {
            render(Page.ReaderFont)
            return true
        }
        if (currentPage != Page.Overview) {
            render(Page.Overview)
            return true
        }
        onClose("back")
        return true
    }

    override fun captureRestoreState(): Any {
        syncChapterBackupPathFromStore()
        return RestoreState(
            statusBarConfig = statusBarConfig,
            bottomTabConfig = bottomTabConfig,
            readerFontConfig = readerFontConfig,
            autoSignInConfig = autoSignInConfig,
            startupOptimizeConfig = startupOptimizeConfig,
            startupTabConfig = startupTabConfig,
            chapterBackupConfig = chapterBackupConfig,
            pageName = currentPage.name,
        )
    }

    private fun buildShell() {
        addView(createTitleBar(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 54)))
        addView(separator(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        val scrollView = ScrollView(activity).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(theme.panelBackground)
        }
        content = LinearLayout(activity).apply {
            orientation = VERTICAL
            setPadding(0, dp(activity, 8), 0, dp(activity, 12))
        }
        rows = ModuleSettingsRows(activity, theme, content)
        scrollView.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scrollView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(separator(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        addView(createFooter(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 54)))
    }

    private fun createTitleBar(): LinearLayout {
        val sideWidth = dp(activity, 88)
        return LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.rowBackground)
            addView(
                TextView(activity).apply {
                    text = "\u2039"
                    textSize = 30f
                    gravity = Gravity.CENTER
                    setTextColor(theme.text)
                    isClickable = true
                    setOnClickListener { handleBack() }
                },
                LayoutParams(sideWidth, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            titleText = TextView(activity).apply {
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(theme.text)
                includeFontPadding = false
            }
            addView(titleText, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(
                TextView(activity).apply {
                    text = "保存并重启"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(theme.accent)
                    isClickable = true
                    setOnClickListener { saveAndRestart() }
                },
                LayoutParams(sideWidth, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    private fun createFooter(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(theme.rowBackground)
            addView(
                footerButton("清空模块配置", theme.text) {
                    confirmResetDrafts()
                },
                LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
            )
            addView(separator(), LayoutParams(1, dp(activity, 28)))
            addView(
                footerButton("保存并应用", theme.accent) {
                    saveAndClose()
                },
                LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
            )
        }
    }

    private fun footerButton(text: String, color: Int, action: () -> Unit): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(color)
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                action()
            }
        }
    }

    private fun render(page: Page) {
        val previousPage = currentPage
        currentPage = page
        titleText.text = page.title
        content.removeAllViews()
        actionRowIndex = 0
        when (page) {
            Page.Overview -> renderOverview()
            Page.ChapterBackup -> renderChapterBackupPage()
            Page.ReaderFont -> renderReaderFontPage()
            Page.ReaderFontManager -> renderReaderFontManagerPage()
            Page.StartupOptimize -> renderStartupOptimizePage()
            Page.StartupTab -> renderStartupTabPage()
            Page.BottomTab -> renderBottomTabPage()
        }
        animatePageTransition(previousPage, page)
    }

    private fun animatePageTransition(previousPage: Page, nextPage: Page) {
        if (previousPage == nextPage || !isAttachedToWindow) {
            content.alpha = 1f
            content.translationX = 0f
            return
        }
        val enteringSecondary = previousPage == Page.Overview && nextPage != Page.Overview
        val offset = dp(activity, if (enteringSecondary) 24 else -18).toFloat()
        content.animate().cancel()
        content.alpha = 0f
        content.translationX = offset
        content.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(170L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun renderOverview() {
        addSectionTitle("外观")
        addOverviewRow(
            title = "状态栏背景优化",
            subtitle = "透明状态栏并按页面取色",
            enabled = statusBarConfig.enabled,
            onToggle = {
                statusBarConfig = statusBarConfig.copy(
                    enabled = !statusBarConfig.enabled,
                    version = nextVersion(statusBarConfig.version),
                )
                render(Page.Overview)
            },
            onOpen = null,
            icon = IconType.STATUS_BAR,
        )
        addOverviewRow(
            title = "底栏 Tab 自定义",
            subtitle = bottomTabSummary(),
            enabled = null,
            onToggle = null,
            onOpen = { render(Page.BottomTab) },
            icon = IconType.BOTTOM_TAB,
        )

        addSectionTitle("阅读")
        addOverviewRow(
            title = "个人章节导出",
            subtitle = "导出免费和已购买章节",
            enabled = chapterBackupConfig.enabled,
            onToggle = {
                chapterBackupConfig = chapterBackupConfig.copy(
                    enabled = !chapterBackupConfig.enabled,
                    version = ChapterBackupConfigStore.nextVersion(chapterBackupConfig),
                )
                render(Page.Overview)
            },
            onOpen = { render(Page.ChapterBackup) },
            icon = IconType.CHAPTER_EXPORT,
        )
        addOverviewRow(
            title = "阅读页字体自定义",
            subtitle = "导入并管理本机字体",
            enabled = readerFontConfig.enabled,
            onToggle = {
                readerFontConfig = readerFontConfig.copy(
                    enabled = !readerFontConfig.enabled,
                    version = nextVersion(readerFontConfig.version),
                )
                render(Page.Overview)
            },
            onOpen = { render(Page.ReaderFont) },
            icon = IconType.FONT,
        )

        addSectionTitle("自动化")
        addOverviewRow(
            title = "启动加速",
            subtitle = startupOptimizeSummary(),
            enabled = startupOptimizeConfig.enabled,
            onToggle = {
                startupOptimizeConfig = startupOptimizeConfig.copy(
                    enabled = !startupOptimizeConfig.enabled,
                    version = StartupOptimizeConfigStore.nextVersion(startupOptimizeConfig),
                )
                render(Page.Overview)
            },
            onOpen = { render(Page.StartupOptimize) },
            icon = IconType.POWER,
        )
        addOverviewRow(
            title = "自动签到",
            subtitle = "每日自动签到，可手动触发",
            enabled = autoSignInConfig.enabled,
            onToggle = {
                autoSignInConfig = autoSignInConfig.copy(
                    enabled = !autoSignInConfig.enabled,
                    version = AutoSignInConfigStore.nextVersion(autoSignInConfig),
                )
                render(Page.Overview)
            },
            onOpen = null,
            icon = IconType.AUTO_SIGN_IN,
            extraActionIcon = IconType.PLAY_PAUSE,
            onExtraAction = { onManualAutoSignIn() },
        )
        addOverviewRow(
            title = "启动默认 Tab",
            subtitle = startupTabSummary(),
            enabled = startupTabConfig.enabled,
            onToggle = {
                startupTabConfig = startupTabConfig.copy(
                    enabled = !startupTabConfig.enabled,
                    version = StartupTabConfigStore.nextVersion(startupTabConfig),
                )
                render(Page.Overview)
            },
            onOpen = { render(Page.StartupTab) },
            icon = IconType.STARTUP_TAB,
        )
    }

    private fun renderChapterBackupPage() {
        addActionRow("选择导出路径", chapterBackupPathLabel()) {
            onChooseChapterBackupDirectory()
            scheduleChapterBackupPathRefresh()
        }
        addActionRow("清除导出路径", "恢复到宿主私有导出路径") {
            if (onClearChapterBackupDirectory()) {
                chapterBackupConfig = chapterBackupConfig.copy(
                    exportTreeUri = null,
                    version = ChapterBackupConfigStore.nextVersion(chapterBackupConfig),
                )
                toast("导出路径已清除")
                render(Page.ChapterBackup)
            } else {
                toast("清除失败，请查看日志")
            }
        }
        addActionRow("导出已缓存章节", "扫描书架中当前账号可读的缓存") {
            if (!chapterBackupConfig.enabled) {
                toast("请先启用个人章节导出")
                return@addActionRow
            }
            syncChapterBackupPathFromStore()
            ChapterBackupConfigStore.writeLocal(activity, chapterBackupConfig)
            onExportCachedChapters()
        }
    }

    private fun renderReaderFontPage() {
        addActionRow(
            title = "导入字体文件",
            subtitle = "选择 .ttf / .otf / .ttc 文件并复制到宿主私有目录",
            icon = IconType.FONT_IMPORT,
        ) {
            if (!readerFontConfig.enabled) {
                toast("请先启用阅读页字体自定义")
                return@addActionRow
            }
            ReaderFontConfigStore.writeLocal(activity, readerFontConfig)
            onImportReaderFonts()
            scheduleReaderFontListRefresh()
        }
        addActionRow(
            title = "管理本地字体",
            subtitle = readerFontManageSummary(),
            icon = IconType.FONT_MANAGE,
        ) {
            render(Page.ReaderFontManager)
        }
    }

    private fun renderReaderFontManagerPage() {
        addInfoRow("拖拽排序", "长按左侧拖拽柄后上下移动，点击右侧按钮删除。")
        val paths = readerFontPaths().toMutableList()
        if (paths.isEmpty()) {
            addInfoRow("暂无本地字体", "请先返回上一页导入字体文件。")
            return
        }
        val container = LinearLayout(activity).apply {
            orientation = VERTICAL
        }
        val fontRows = ModuleSettingsReaderFontRows(
            activity = activity,
            theme = theme,
            paths = paths,
            currentPath = currentReaderFontPath(),
            onSelect = { path -> selectReaderFont(path) },
            onOrderChanged = { ordered ->
                ReaderFontConfigStore.writeFonts(activity, ordered)
                toast("字体顺序已保存")
            },
            onDelete = { path -> deleteReaderFont(path) },
            onRenderRequested = {
                render(Page.ReaderFontManager)
            },
        )
        paths.forEach { path ->
            container.addView(fontRows.createRow(path, container), fontRows.rowParams())
        }
        content.addView(container, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun renderStartupTabPage() {
        addSectionTitle("启动目标")
        val tabIconFactory = ModuleSettingsTabIconFactory(activity, theme)
        BottomTabConfigStore.TABS.forEach { tab ->
            val selected = startupTabConfig.tabKey == tab.key
            addChoiceRow(
                tab.label,
                selected,
                tabIconFactory.create(
                    spec = tab,
                    color = if (selected) theme.accent else theme.subText,
                    alpha = if (selected) 1f else 0.72f,
                ),
            ) {
                startupTabConfig = startupTabConfig.copy(
                    tabKey = tab.key,
                    version = StartupTabConfigStore.nextVersion(startupTabConfig),
                )
                render(Page.StartupTab)
            }
        }
    }

    private fun renderStartupOptimizePage() {
        addSectionTitle("启动链路")
        addOverviewRow(
            title = "跳过本站开屏",
            subtitle = "启动时不进入缓存广告或活动页",
            enabled = startupOptimizeConfig.skipSelfSplash,
            onToggle = {
                startupOptimizeConfig = startupOptimizeConfig.copy(
                    skipSelfSplash = !startupOptimizeConfig.skipSelfSplash,
                    version = StartupOptimizeConfigStore.nextVersion(startupOptimizeConfig),
                )
                render(Page.StartupOptimize)
            },
            onOpen = null,
            icon = IconType.AD,
        )
        addOverviewRow(
            title = "跳过第三方开屏",
            subtitle = "跳过 Tobid/WindMill 启动广告等待",
            enabled = startupOptimizeConfig.skipThirdPartySplash,
            onToggle = {
                startupOptimizeConfig = startupOptimizeConfig.copy(
                    skipThirdPartySplash = !startupOptimizeConfig.skipThirdPartySplash,
                    version = StartupOptimizeConfigStore.nextVersion(startupOptimizeConfig),
                )
                render(Page.StartupOptimize)
            },
            onOpen = null,
            icon = IconType.PLAY,
        )
        addOverviewRow(
            title = "禁用启动页预取",
            subtitle = "停止拉取和下载下次开屏素材",
            enabled = startupOptimizeConfig.disableStartPagePrefetch,
            onToggle = {
                startupOptimizeConfig = startupOptimizeConfig.copy(
                    disableStartPagePrefetch = !startupOptimizeConfig.disableStartPagePrefetch,
                    version = StartupOptimizeConfigStore.nextVersion(startupOptimizeConfig),
                )
                render(Page.StartupOptimize)
            },
            onOpen = null,
            icon = IconType.DOWNLOAD,
        )
        addOverviewRow(
            title = "兜底跳过开屏页",
            subtitle = "旧开屏页出现时立即走宿主跳转",
            enabled = startupOptimizeConfig.skipAdvertisementActivity,
            onToggle = {
                startupOptimizeConfig = startupOptimizeConfig.copy(
                    skipAdvertisementActivity = !startupOptimizeConfig.skipAdvertisementActivity,
                    version = StartupOptimizeConfigStore.nextVersion(startupOptimizeConfig),
                )
                render(Page.StartupOptimize)
            },
            onOpen = null,
            icon = IconType.CHECK,
        )
    }

    private fun renderBottomTabPage() {
        addInfoRow("拖拽排序", "长按左侧拖拽柄后上下移动，点击右侧状态可显示或隐藏。")
        val container = LinearLayout(activity).apply {
            orientation = VERTICAL
        }
        val tabRows = ModuleSettingsBottomTabRows(
            activity = activity,
            theme = theme,
            state = bottomTabState,
            onStateChanged = {
                bottomTabConfig = bottomTabState.toConfig()
            },
            onRenderRequested = {
                render(Page.BottomTab)
            },
            toast = ::toast,
        )
        bottomTabState.order.forEach { key ->
            val spec = BottomTabConfigStore.tabByKey(key) ?: return@forEach
            container.addView(tabRows.createRow(spec, container), tabRows.rowParams())
        }
        content.addView(container, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addActionRow("恢复默认底栏", "恢复为宿主原始顺序和全部显示") {
            bottomTabState.reset()
            bottomTabConfig = bottomTabState.toConfig()
            toast("已恢复默认，保存后生效")
            render(Page.BottomTab)
        }
    }

    private fun addOverviewRow(
        title: String,
        subtitle: String,
        enabled: Boolean?,
        onToggle: (() -> Unit)?,
        onOpen: (() -> Unit)?,
        icon: IconType? = null,
        extraActionIcon: IconType? = null,
        onExtraAction: (() -> Unit)? = null,
    ) {
        rows.addOverviewRow(title, subtitle, enabled, onToggle, onOpen, icon, extraActionIcon, onExtraAction)
    }

    private fun addActionRow(title: String, subtitle: String, icon: IconType? = null, onClick: () -> Unit) {
        rows.addActionRow(title, subtitle, icon ?: defaultActionIcon(actionRowIndex), onClick)
        actionRowIndex += 1
    }

    private fun defaultActionIcon(index: Int): IconType? {
        return when (currentPage) {
            Page.ChapterBackup -> when (index) {
                0 -> IconType.FOLDER_OPEN
                1 -> IconType.DELETE
                else -> IconType.CHAPTER_EXPORT
            }
            Page.BottomTab -> IconType.RESET
            else -> null
        }
    }

    private fun addChoiceRow(title: String, selected: Boolean, leadingIcon: View? = null, onClick: () -> Unit) {
        rows.addChoiceRow(title, selected, leadingIcon, onClick)
    }

    private fun addInfoRow(title: String, subtitle: String) {
        rows.addInfoRow(title, subtitle)
    }

    private fun addSectionTitle(title: String) {
        rows.addSectionTitle(title)
    }

    private fun separator(): View {
        return View(activity).apply {
            setBackgroundColor(theme.separator)
        }
    }

    private fun saveAndClose() {
        syncChapterBackupPathFromStore()
        onSave(
            statusBarConfig,
            bottomTabConfig,
            readerFontConfig,
            autoSignInConfig,
            startupOptimizeConfig,
            startupTabConfig,
            chapterBackupConfig,
        )
        onClose("save")
    }

    private fun saveAndRestart() {
        syncChapterBackupPathFromStore()
        onSave(
            statusBarConfig,
            bottomTabConfig,
            readerFontConfig,
            autoSignInConfig,
            startupOptimizeConfig,
            startupTabConfig,
            chapterBackupConfig,
        )
        onClose("save-restart")
        activity.window.decorView.postDelayed({ onRestartHost() }, 250L)
    }

    private fun confirmResetDrafts() {
        ModuleSettingsConfirmDialog(activity, theme).show(
            title = "清空模块配置",
            message = "将当前设置草稿恢复为默认值，保存并应用后生效。",
            confirmText = "清空",
        ) {
            resetDrafts()
        }
    }

    private fun resetDrafts() {
        statusBarConfig = StatusBarConfigStore.defaultConfig()
        bottomTabConfig = BottomTabConfigStore.defaultConfig()
        bottomTabState = BottomTabPanelState.from(bottomTabConfig)
        readerFontConfig = ReaderFontConfigStore.defaultConfig()
        autoSignInConfig = AutoSignInConfigStore.defaultConfig()
        startupOptimizeConfig = StartupOptimizeConfigStore.defaultConfig()
        startupTabConfig = StartupTabConfigStore.defaultConfig()
        chapterBackupConfig = ChapterBackupConfigStore.defaultConfig()
        toast("已恢复默认草稿，保存后生效")
        render(currentPage)
    }

    private fun bottomTabSummary(): String {
        if (!bottomTabConfig.enabled) {
            return "跟随宿主默认"
        }
        val visibleCount = bottomTabConfig.order.count { it !in bottomTabConfig.hidden }
        return "已自定义：显示 $visibleCount/${BottomTabConfigStore.TABS.size} 个"
    }

    private fun startupTabSummary(): String {
        if (!startupTabConfig.enabled) {
            return "跟随宿主默认"
        }
        val label = BottomTabConfigStore.tabByKey(startupTabConfig.tabKey)?.label ?: "书城"
        return "启动时进入：$label"
    }

    private fun startupOptimizeSummary(): String {
        if (!startupOptimizeConfig.enabled) {
            return "未启用"
        }
        val enabledItems = listOf(
            startupOptimizeConfig.skipSelfSplash,
            startupOptimizeConfig.skipThirdPartySplash,
            startupOptimizeConfig.disableStartPagePrefetch,
            startupOptimizeConfig.skipAdvertisementActivity,
        ).count { it }
        return "已启用 $enabledItems/4 项"
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

    private fun scheduleChapterBackupPathRefresh() {
        listOf(600L, 1500L, 3000L, 6000L, 12000L, 24000L).forEach { delay ->
            overlay.postDelayed(
                {
                    if (syncChapterBackupPathFromStore()) {
                        render(currentPage)
                    }
                },
                delay,
            )
        }
    }

    private fun scheduleReaderFontListRefresh() {
        listOf(800L, 1600L, 3200L, 6400L, 12000L).forEach { delay ->
            overlay.postDelayed(
                {
                    if (currentPage == Page.ReaderFont || currentPage == Page.ReaderFontManager) {
                        render(currentPage)
                    }
                },
                delay,
            )
        }
    }

    private fun readerFontManageSummary(): String {
        val count = readerFontPaths().size
        return if (count == 0) "暂无本地字体" else "已添加 $count 个字体"
    }

    private fun readerFontPaths(): List<String> {
        return ReaderFontConfigStore.readFonts(activity)
            .filter { isFontFile(it) }
            .distinctBy { File(it).absolutePath }
    }

    private fun deleteReaderFont(path: String) {
        val target = File(path).absolutePath
        val remaining = ReaderFontConfigStore.readFonts(activity)
            .filterNot { File(it).absolutePath == target }
        ReaderFontConfigStore.writeFonts(activity, remaining)
        deletePrivateFontCopy(path)
        if (File(currentReaderFontPath()).absolutePath == target) {
            resetCurrentReaderFont()
        }
        toast("已删除 ${fontDisplayName(path)}")
        render(Page.ReaderFontManager)
    }

    private fun selectReaderFont(path: String) {
        val file = File(path)
        if (!file.isFile) {
            toast("字体文件不存在")
            return
        }
        val front = activity.getSharedPreferences("front", Activity.MODE_PRIVATE)
        val textType = front.getString("textType1", "jian").orEmpty().ifBlank { "jian" }
        front.edit()
            .putString("textTypePath1", file.absolutePath)
            .putString("textType1", textType)
            .apply()
        toast("已选择 ${fontDisplayName(path)}")
        render(Page.ReaderFontManager)
    }

    private fun deletePrivateFontCopy(path: String) {
        runCatching {
            val fontsDir = File(activity.filesDir, "cwmhook/fonts").canonicalFile
            val file = File(path).canonicalFile
            if (file.path.startsWith(fontsDir.path + File.separator) && file.isFile) {
                file.delete()
            }
        }
    }

    private fun resetCurrentReaderFont() {
        val front = activity.getSharedPreferences("front", Activity.MODE_PRIVATE)
        val textType = front.getString("textType1", "jian").orEmpty().ifBlank { "jian" }
        front.edit()
            .putString("textTypePath1", "syht.otf")
            .putString("textType1", textType)
            .apply()
    }

    private fun currentReaderFontPath(): String {
        return activity.getSharedPreferences("front", Activity.MODE_PRIVATE)
            .getString("textTypePath1", "syht.otf")
            .orEmpty()
            .ifBlank { "syht.otf" }
    }

    private fun fontDisplayName(path: String): String {
        val name = File(path).name.ifBlank { path }
        return name.substringBeforeLast('.', name)
    }

    private fun isFontFile(path: String): Boolean {
        val lower = path.lowercase(java.util.Locale.US)
        return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")
    }

    private fun nextVersion(version: Int): Int {
        return if (version == Int.MAX_VALUE) 1 else version + 1
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    data class RestoreState(
        val statusBarConfig: StatusBarConfig,
        val bottomTabConfig: BottomTabConfig,
        val readerFontConfig: ReaderFontConfig,
        val autoSignInConfig: AutoSignInConfig,
        val startupOptimizeConfig: StartupOptimizeConfig,
        val startupTabConfig: StartupTabConfig,
        val chapterBackupConfig: ChapterBackupConfig,
        val pageName: String,
    )

    private enum class Page(val title: String) {
        Overview("模块设置"),
        ChapterBackup("个人章节导出"),
        ReaderFont("阅读页字体自定义"),
        ReaderFontManager("管理本地字体"),
        StartupOptimize("启动加速"),
        StartupTab("启动默认 Tab"),
        BottomTab("底栏 Tab 自定义"),
    }

    private companion object {
        const val CHAPTER_EXPORT_LOG_TAG = "CWMHook.ChapterExport"

        private fun String.toPage(): Page {
            return runCatching { Page.valueOf(this) }.getOrDefault(Page.Overview)
        }
    }
}

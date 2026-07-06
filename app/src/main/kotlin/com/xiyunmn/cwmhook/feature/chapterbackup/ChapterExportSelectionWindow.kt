package com.xiyunmn.cwmhook.feature.chapterbackup

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoPackages
import java.util.Locale

internal class ChapterExportSelectionWindow(
    private val activity: Activity,
    private val exporter: ChapterBackupExporter,
    private val bookInfo: Any?,
    private val downloadType: String? = null,
    private val restoreState: RestoreState? = null,
) {
    private var dialog: Dialog? = null
    private var rootView: View? = null
    private var closing = false
    private lateinit var theme: HostPageTheme
    private var book: ChapterBackupBook? = null
    private var customBaseName: String? = null
    private var candidates = emptyList<ChapterBackupCandidate>()
    private var candidateById = emptyMap<String, ChapterBackupCandidate>()
    private var rows = emptyList<Row>()
    private val selectedIds = LinkedHashSet<String>()
    private val expandedDivisions = LinkedHashSet<String>()
    private val expandedGroups = LinkedHashSet<String>()

    private lateinit var listView: ListView
    private lateinit var selectAllText: TextView
    private lateinit var directoryText: TextView
    private lateinit var summaryText: TextView
    private lateinit var estimateText: TextView
    private lateinit var exportButton: TextView
    private var pendingThemeRefreshReason = ""
    private val expandCollapseInterpolator = DecelerateInterpolator()
    private val themeRefreshRunnable = Runnable {
        refreshTheme(pendingThemeRefreshReason.ifBlank { "SkinChangeHelper" })
    }

    fun show() {
        runCatching {
            theme = HostPageTheme.from(activity)
            book = restoreState?.book ?: bookInfo?.toChapterBackupBook()
            if (customBaseName.isNullOrBlank()) {
                customBaseName = restoreState?.customBaseName
                    ?: book?.defaultExportBaseName()
            }
            val root = createRoot()
            root.translationX = activity.resources.displayMetrics.widthPixels.toFloat()
            rootView = root
            dialog = createDialog(root)
            registerWindow(this)
            playEnterAnimation(root)
            ModuleFileLogger.i(TAG, "Chapter export selector shown: activity=${activity.javaClass.name}")
            refreshDirectoryLabel()
            if (restoreState?.hasCandidates == true) {
                applyRestoreState(restoreState)
            } else {
                loadCandidates()
            }
        }.onFailure { throwable ->
            ModuleFileLogger.e(TAG, "Failed to show chapter export selector", throwable)
            Toast.makeText(activity, "导出页面打开失败，请查看日志", Toast.LENGTH_LONG).show()
            dismiss()
        }
    }

    private fun createRoot(): View {
        val root = FrameLayout(activity).apply {
            setBackgroundColor(theme.mainBackground)
            isClickable = true
            isFocusable = true
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.mainBackground)
        }
        root.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        content.addView(createHeader(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(separator(theme.divider), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        listView = ListView(activity).apply {
            divider = null
            dividerHeight = 0
            cacheColorHint = Color.TRANSPARENT
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            setBackgroundColor(theme.rowBackground)
            adapter = LoadingAdapter()
        }
        content.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(createFooter(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun createDialog(root: View): Dialog {
        return Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(root)
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        this@ChapterExportSelectionWindow.dismiss()
                    }
                    true
                } else {
                    false
                }
            }
            setOnDismissListener { handleDialogDismissed() }
            show()
            window?.apply { configureWindow(this) }
        }
    }

    private fun configureWindow(window: Window) {
        window.setBackgroundDrawable(ColorDrawable(theme.mainBackground))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.decorView.setBackgroundColor(theme.mainBackground)
        window.decorView.isClickable = true
        applySystemBars(window)
    }

    private fun handleDialogDismissed() {
        if (!closing && !activity.isFinishing) {
            RestoreState.capture(this)?.let { state ->
                pendingRestore = state
                ModuleFileLogger.i(TAG, "Chapter export selector pending restore captured: book=${state.book.bookId}, loaded=${state.hasCandidates}")
            }
        }
        unregisterWindow(this)
        rootView?.removeCallbacks(themeRefreshRunnable)
        dialog = null
        rootView = null
        closing = false
    }

    private fun loadCandidates() {
        val currentBook = book
        if (currentBook == null && bookInfo == null) {
            Toast.makeText(activity, "未识别当前作品", Toast.LENGTH_LONG).show()
            dismiss()
            return
        }
        val callback = object : ChapterBackupExporter.CandidateCallback {
            override fun onStarted() {
                if (!isShowing()) {
                    return
                }
                ModuleFileLogger.i(TAG, "Chapter export candidates loading")
                summaryText.text = "已选择：0 章"
                estimateText.text = "预计大小：0 KB"
                exportButton.isEnabled = false
                exportButton.alpha = 0.5f
            }

            override fun onSuccess(book: ChapterBackupBook, candidates: List<ChapterBackupCandidate>) {
                if (!isShowing()) {
                    return
                }
                ModuleFileLogger.i(
                    TAG,
                    "Chapter export candidates loaded: book=${book.bookId}, total=${candidates.size}, available=${candidates.count { it.isExportable() }}",
                )
                this@ChapterExportSelectionWindow.book = book
                if (customBaseName.isNullOrBlank()) {
                    customBaseName = book.defaultExportBaseName()
                }
                this@ChapterExportSelectionWindow.candidates = candidates
                candidateById = candidates.associateBy { it.chapterId }
                initializeExpandedState(candidates)
                rows = buildRows(candidates)
                selectedIds.clear()
                candidates.filter { it.isExportable() }.forEach { selectedIds.add(it.chapterId) }
                listView.adapter = RowAdapter()
                listView.visibility = View.VISIBLE
                listView.post {
                    listView.invalidateViews()
                    ModuleFileLogger.i(
                        TAG,
                        "Chapter export rows rendered: rows=${rows.size}, selected=${selectedIds.size}, first=${rows.firstOrNull()?.javaClass?.simpleName ?: "none"}",
                    )
                }
                updateFooter()
            }

            override fun onFailure(message: String) {
                if (!isShowing()) {
                    return
                }
                ModuleFileLogger.w(TAG, "Chapter export candidates load failed: $message")
                Toast.makeText(activity, message.ifBlank { "读取目录失败" }, Toast.LENGTH_LONG).show()
                dismiss()
            }
        }
        if (currentBook != null) {
            exporter.loadBookCandidates(activity, currentBook, callback)
            return
        }
        exporter.loadBookCandidates(
            activity,
            bookInfo,
            callback,
        )
    }

    private fun applyRestoreState(state: RestoreState) {
        book = state.book
        customBaseName = state.customBaseName
        candidates = state.candidates
        candidateById = state.candidates.associateBy { it.chapterId }
        expandedDivisions.clear()
        expandedDivisions.addAll(state.expandedDivisions)
        expandedGroups.clear()
        expandedGroups.addAll(state.expandedGroups)
        selectedIds.clear()
        selectedIds.addAll(state.selectedIds)
        rows = buildRows(candidates)
        listView.adapter = RowAdapter()
        listView.visibility = View.VISIBLE
        updateFooter()
        listView.post {
            if (rows.isNotEmpty()) {
                listView.setSelection(state.firstVisiblePosition.coerceIn(0, rows.lastIndex))
            }
            ModuleFileLogger.i(TAG, "Chapter export selector restored: rows=${rows.size}, selected=${selectedIds.size}")
        }
    }

    private fun createHeader(): RelativeLayout {
        return RelativeLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.titleBackground)

            val leftId = View.generateViewId()
            val back = ImageView(activity).apply {
                id = leftId
                scaleType = ImageView.ScaleType.CENTER
                val icon = hostDrawableId("icon_title_return")
                if (icon != 0) {
                    setImageResource(icon)
                }
                contentDescription = "返回"
                isClickable = true
                setOnClickListener { dismiss() }
            }
            addView(
                back,
                RelativeLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginStart = dp(4)
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )

            val rightId = View.generateViewId()
            selectAllText = TextView(activity).apply {
                id = rightId
                text = "全选"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(theme.titleRightText)
                includeFontPadding = false
                isClickable = true
                setOnClickListener { selectAllAvailable() }
            }
            addView(
                selectAllText,
                RelativeLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(4)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )

            addView(
                TextView(activity).apply {
                    text = "导出章节"
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(theme.titleText)
                    includeFontPadding = false
                    maxLines = 1
                },
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginStart = dp(80)
                    marginEnd = dp(80)
                    addRule(RelativeLayout.CENTER_IN_PARENT)
                },
            )
        }
    }

    private fun createFooter(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.rowBackground)
            addView(separator(theme.divider), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            addView(createDirectoryRow(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            addView(separator(theme.divider), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            addView(createActionRow(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
            addView(separator(theme.divider), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            addView(createFormatRow(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }
    }

    private fun createFormatRow(): RelativeLayout {
        return RelativeLayout(activity).apply {
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(theme.rowBackground)
            addView(
                createRowLabel("导出格式："),
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )
            addView(
                TextView(activity).apply {
                    text = "TXT"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(theme.accentText)
                    includeFontPadding = false
                },
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(84)
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )
            addView(
                TextView(activity).apply {
                    text = "导出命名"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(theme.buttonText)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    isClickable = true
                    background = exportButtonBackground(enabled = true)
                    setOnClickListener { showNamingDialog() }
                },
                RelativeLayout.LayoutParams(dp(100), dp(30)).apply {
                    marginEnd = dp(5)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )
        }
    }

    private fun createDirectoryRow(): RelativeLayout {
        return RelativeLayout(activity).apply {
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(theme.rowBackground)
            isClickable = true
            setOnClickListener {
                ChapterBackupFeature.launchDirectoryPicker(activity)
                scheduleDirectoryRefresh()
            }

            addView(
                createRowLabel("导出路径："),
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )
            directoryText = TextView(activity).apply {
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                setSingleLine(false)
            }
            addView(
                directoryText,
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginStart = dp(92)
                    marginEnd = dp(18)
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                },
            )
            addView(
                createChevron(),
                RelativeLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )
        }
    }

    private fun createActionRow(): RelativeLayout {
        return RelativeLayout(activity).apply {
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(theme.rowBackground)

            val buttonId = View.generateViewId()
            exportButton = TextView(activity).apply {
                id = buttonId
                text = "开始导出"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(theme.buttonText)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                isEnabled = false
                alpha = 0.5f
                background = exportButtonBackground(enabled = false)
                setOnClickListener { exportSelected() }
            }
            addView(
                exportButton,
                RelativeLayout.LayoutParams(dp(100), dp(30)).apply {
                    marginEnd = dp(5)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )

            val statBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            summaryText = TextView(activity).apply {
                text = "已选择：0 章"
                textSize = 15f
                setTextColor(theme.primaryText)
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            estimateText = TextView(activity).apply {
                text = "预计大小：0 KB"
                textSize = 13f
                setTextColor(theme.secondaryText)
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            statBox.addView(summaryText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)))
            statBox.addView(estimateText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
            addView(
                statBox,
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(12)
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.START_OF, buttonId)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )
        }
    }

    private fun createRowLabel(text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(theme.primaryText)
            includeFontPadding = false
        }
    }

    private fun createChevron(): TextView {
        return TextView(activity).apply {
            text = "›"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(theme.tertiaryText)
            includeFontPadding = false
        }
    }

    private fun showNamingDialog() {
        val currentBook = book ?: run {
            Toast.makeText(activity, "目录尚未读取完成", Toast.LENGTH_SHORT).show()
            return
        }
        val defaultName = currentBook.defaultExportBaseName()
        val editText = EditText(activity).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(96))
            setText(customBaseName?.takeIf { it.isNotBlank() } ?: defaultName)
            setSelectAllOnFocus(true)
            highlightColor = NAMING_SELECTION_COLOR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = cursorDrawable()
            }
            setTextColor(theme.primaryText)
            setHintTextColor(theme.secondaryText)
            hint = ""
            textSize = 16f
            background = null
            includeFontPadding = false
            setPadding(dp(14), 0, dp(42), 0)
        }
        val namingDialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(theme.rowBackground, dp(16).toFloat())
            addView(
                TextView(activity).apply {
                    text = "导出命名"
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(theme.titleText)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)),
            )
            addView(
                FrameLayout(activity).apply {
                    setPadding(dp(20), 0, dp(20), dp(18))
                    addView(
                        FrameLayout(activity).apply {
                            background = strokeRoundRect(Color.TRANSPARENT, theme.divider, dp(14).toFloat(), 1)
                            addView(
                                editText,
                                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.CENTER_VERTICAL),
                            )
                            addView(
                                TextView(activity).apply {
                                    text = "×"
                                    textSize = 18f
                                    gravity = Gravity.CENTER
                                    setTextColor(Color.WHITE)
                                    includeFontPadding = false
                                    background = roundRect(theme.tertiaryText, dp(12).toFloat())
                                    isClickable = true
                                    setOnClickListener { editText.text?.clear() }
                                },
                                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.END or Gravity.CENTER_VERTICAL).apply {
                                    marginEnd = dp(12)
                                },
                            )
                        },
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(separator(theme.divider), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        TextView(activity).apply {
                            text = "取消"
                            textSize = 17f
                            gravity = Gravity.CENTER
                            setTextColor(theme.accentText)
                            includeFontPadding = false
                            isClickable = true
                            setOnClickListener { namingDialog.dismiss() }
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
                    )
                    addView(separator(theme.divider), LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT))
                    addView(
                        TextView(activity).apply {
                            text = "确定"
                            textSize = 17f
                            gravity = Gravity.CENTER
                            setTextColor(theme.accentText)
                            typeface = Typeface.DEFAULT_BOLD
                            includeFontPadding = false
                            isClickable = true
                            setOnClickListener {
                                customBaseName = editText.text?.toString()?.trim()?.ifBlank { defaultName } ?: defaultName
                                Toast.makeText(activity, "导出文件名已更新", Toast.LENGTH_SHORT).show()
                                namingDialog.dismiss()
                            }
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)),
            )
        }
        namingDialog.setContentView(
            FrameLayout(activity).apply {
                addView(
                    panel,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            },
        )
        namingDialog.setOnShowListener {
            namingDialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.55f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setLayout((activity.resources.displayMetrics.widthPixels * 0.84f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                applySystemBars(this)
            }
            editText.requestFocus()
            editText.selectAll()
            editText.postDelayed({
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }, 120L)
        }
        namingDialog.show()
        namingDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun exportSelected() {
        val currentBook = book ?: return
        val selected = candidates
            .filter { it.chapterId in selectedIds }
            .sortedWith(compareBy<ChapterBackupCandidate> { it.index < 0 }.thenBy { it.index })
        exporter.exportSelectedChaptersWithHostDownload(
            activity,
            currentBook,
            selected,
            downloadType,
            currentNaming(currentBook),
            object : ChapterBackupExporter.DownloadCallback {
                override fun onStarted() {
                    ModuleFileLogger.i(TAG, "Selected chapter export started: selected=${selected.size}")
                    exportButton.isEnabled = false
                    exportButton.alpha = 0.5f
                    summaryText.text = "准备导出..."
                }

                override fun onDownloadStarted(chapterCount: Int) {
                    summaryText.text = "正在下载：$chapterCount 章"
                    estimateText.text = "下载完成后自动导出"
                }

                override fun onDownloadProgress(progress: Int) {
                    if (progress >= 0) {
                        summaryText.text = "正在下载：$progress%"
                    }
                }

                override fun onSuccess(result: ChapterBackupResult) {
                    ModuleFileLogger.i(TAG, "Selected chapter export success: chapters=${result.chapterCount}, output=${result.outputLabel}")
                    Toast.makeText(activity, "已导出 ${result.chapterCount} 章", Toast.LENGTH_LONG).show()
                    updateFooter()
                    estimateText.text = "已导出：${result.chapterCount} 章"
                }

                override fun onFailure(message: String) {
                    ModuleFileLogger.w(TAG, "Selected chapter export failed: $message")
                    Toast.makeText(activity, message.ifBlank { "导出失败" }, Toast.LENGTH_LONG).show()
                    updateFooter()
                }
            },
        )
    }

    private fun currentNaming(book: ChapterBackupBook): ChapterBackupNaming {
        val directoryName = book.defaultExportBaseName()
        val fileName = customBaseName?.trim()?.ifBlank { directoryName } ?: directoryName
        return ChapterBackupNaming(directoryName = directoryName, fileName = fileName)
    }

    private fun selectAllAvailable() {
        val available = availableChapters()
        val allSelected = available.isNotEmpty() && available.all { it.chapterId in selectedIds }
        selectedIds.clear()
        if (!allSelected) {
            available.forEach { selectedIds.add(it.chapterId) }
        }
        notifyRowsChanged()
        updateFooter()
    }

    private fun toggleGroup(row: Row.Group) {
        val availableIds = row.chapterIds
            .mapNotNull(candidateById::get)
            .filter { it.isExportable() }
            .map { it.chapterId }
        if (availableIds.isEmpty()) {
            Toast.makeText(activity, "这一组没有可导出的章节", Toast.LENGTH_SHORT).show()
            return
        }
        activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val allSelected = availableIds.all { it in selectedIds }
        if (allSelected) {
            selectedIds.removeAll(availableIds.toSet())
        } else {
            selectedIds.addAll(availableIds)
        }
        notifyRowsChanged()
        updateFooter()
    }

    private fun toggleGroupExpanded(row: Row.Group) {
        if (!expandedGroups.add(row.id)) {
            expandedGroups.remove(row.id)
        }
        rebuildVisibleRows()
    }

    private fun toggleDivision(row: Row.Division) {
        val availableIds = candidates
            .filter { it.displayDivisionTitle() == row.title && it.isExportable() }
            .map { it.chapterId }
        if (availableIds.isEmpty()) {
            Toast.makeText(activity, "这一卷没有可导出的章节", Toast.LENGTH_SHORT).show()
            return
        }
        activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val allSelected = availableIds.all { it in selectedIds }
        if (allSelected) {
            selectedIds.removeAll(availableIds.toSet())
        } else {
            selectedIds.addAll(availableIds)
        }
        notifyRowsChanged()
        updateFooter()
    }

    private fun toggleDivisionExpanded(row: Row.Division) {
        if (!expandedDivisions.add(row.title)) {
            expandedDivisions.remove(row.title)
        }
        rebuildVisibleRows()
    }

    private fun toggle(candidate: ChapterBackupCandidate) {
        if (!candidate.isExportable()) {
            Toast.makeText(activity, "该章节未授权且无本地缓存", Toast.LENGTH_SHORT).show()
            return
        }
        activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (!selectedIds.add(candidate.chapterId)) {
            selectedIds.remove(candidate.chapterId)
        }
        notifyRowsChanged()
        updateFooter()
    }

    private fun updateFooter() {
        val available = availableChapters()
        val selectedCount = selectedIds.size
        val selectedBytes = candidates
            .asSequence()
            .filter { it.chapterId in selectedIds }
            .sumOf { it.estimatedBytes.coerceAtLeast(0L) }
        val downloadCount = candidates.count { it.chapterId in selectedIds && it.needsDownload() }
        summaryText.text = if (downloadCount > 0) {
            "已选择：$selectedCount 章，待下载：$downloadCount 章"
        } else {
            "已选择：$selectedCount 章"
        }
        estimateText.text = if (downloadCount > 0) {
            "已缓存约 ${formatBytes(selectedBytes, selectedCount - downloadCount)}"
        } else {
            "预计大小：${formatBytes(selectedBytes, selectedCount)}"
        }
        val allSelected = available.isNotEmpty() && available.all { it.chapterId in selectedIds }
        selectAllText.text = if (allSelected) "取消全选" else "全选"
        selectAllText.isEnabled = available.isNotEmpty()
        selectAllText.alpha = if (available.isNotEmpty()) 1f else 0.45f
        exportButton.isEnabled = selectedCount > 0
        exportButton.alpha = if (selectedCount > 0) 1f else 0.5f
        exportButton.background = exportButtonBackground(selectedCount > 0)
    }

    private fun refreshDirectoryLabel() {
        val config = ChapterBackupConfigStore.readLocal(activity)
        val selected = !config.exportTreeUri.isNullOrBlank()
        directoryText.text = ChapterBackupDestination(activity, config.exportTreeUri, TAG).exportDirectoryLabel()
        directoryText.setTextColor(if (selected) theme.titleRightText else theme.secondaryText)
    }

    private fun scheduleDirectoryRefresh() {
        listOf(800L, 1800L, 3500L, 6500L).forEach { delay ->
            directoryText.postDelayed({ refreshDirectoryLabel() }, delay)
        }
    }

    private fun scheduleThemeRefresh(reason: String) {
        val root = rootView ?: return
        pendingThemeRefreshReason = reason
        root.removeCallbacks(themeRefreshRunnable)
        root.postDelayed(themeRefreshRunnable, 220L)
    }

    private fun isShowing(): Boolean {
        return dialog?.isShowing == true && rootView != null
    }

    private fun firstVisiblePositionOrZero(): Int {
        return if (::listView.isInitialized) listView.firstVisiblePosition else 0
    }

    private fun notifyRowsChanged() {
        (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    private fun rebuildVisibleRows() {
        val first = if (::listView.isInitialized) listView.firstVisiblePosition else 0
        rows = buildRows(candidates)
        notifyRowsChanged()
        if (::listView.isInitialized && rows.isNotEmpty()) {
            listView.setSelection(first.coerceIn(0, rows.lastIndex))
            animateVisibleRowsAfterExpandCollapse()
        }
    }

    private fun animateVisibleRowsAfterExpandCollapse() {
        listView.post {
            val offset = dp(4).toFloat()
            repeat(listView.childCount) { index ->
                val child = listView.getChildAt(index) ?: return@repeat
                child.animate().cancel()
                child.alpha = 0.72f
                child.translationY = offset
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((index * EXPAND_COLLAPSE_STAGGER_MS).coerceAtMost(EXPAND_COLLAPSE_MAX_STAGGER_MS))
                    .setDuration(EXPAND_COLLAPSE_ANIM_MS)
                    .setInterpolator(expandCollapseInterpolator)
                    .start()
            }
        }
    }

    private fun availableChapters(): List<ChapterBackupCandidate> {
        return candidates.filter { it.isExportable() }
    }

    private fun ChapterBackupCandidate.displayDivisionTitle(): String {
        return divisionTitle.ifBlank { "作品相关" }
    }

    private fun ChapterBackupCandidate.isExportable(): Boolean {
        return cached || authorized
    }

    private fun ChapterBackupCandidate.needsDownload(): Boolean {
        return !cached && authorized
    }

    private fun dismiss() {
        val currentDialog = dialog ?: return
        if (closing) {
            return
        }
        val root = rootView
        if (root == null || root.width <= 0) {
            closing = true
            currentDialog.dismiss()
            return
        }
        closing = true
        root.animate()
            .translationX(root.width.toFloat())
            .setDuration(PAGE_ANIM_MS)
            .withEndAction {
                currentDialog.dismiss()
            }
            .start()
    }

    private fun playEnterAnimation(root: View) {
        root.post {
            root.translationX = root.width.coerceAtLeast(activity.resources.displayMetrics.widthPixels).toFloat()
            root.animate()
                .translationX(0f)
                .setDuration(PAGE_ANIM_MS)
                .start()
        }
    }

    private fun refreshTheme(reason: String) {
        val currentDialog = dialog ?: return
        if (closing) {
            return
        }
        val first = if (::listView.isInitialized) listView.firstVisiblePosition else 0
        theme = HostPageTheme.from(activity)
        val root = createRoot()
        rootView = root
        currentDialog.setContentView(root)
        currentDialog.window?.apply { configureWindow(this) }
        if (book != null || candidates.isNotEmpty()) {
            rows = buildRows(candidates)
            listView.adapter = RowAdapter()
            updateFooter()
            refreshDirectoryLabel()
            if (rows.isNotEmpty()) {
                listView.setSelection(first.coerceIn(0, rows.lastIndex))
            }
        } else {
            listView.adapter = LoadingAdapter()
        }
        ChapterBackupSkinBridge.refreshTree(root)
        ModuleFileLogger.i(TAG, "Chapter export selector theme refreshed: reason=$reason skin=${theme.skinKey}")
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = theme.titleBackground
            window.navigationBarColor = theme.rowBackground
        }
        val lightStatusBar = isLightColor(theme.titleBackground)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decor = window.decorView
            val lightStatusFlag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            decor.systemUiVisibility = if (lightStatusBar) {
                decor.systemUiVisibility or lightStatusFlag
            } else {
                decor.systemUiVisibility and lightStatusFlag.inv()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appearance = if (lightStatusBar) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0
            window.insetsController?.setSystemBarsAppearance(
                appearance,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        }
    }

    private fun buildRows(candidates: List<ChapterBackupCandidate>): List<Row> {
        if (candidates.isEmpty()) {
            return listOf(Row.Empty)
        }
        val built = ArrayList<Row>()
        candidates.groupBy { it.displayDivisionTitle() }.forEach { (divisionTitle, chapters) ->
            built += Row.Division(divisionTitle, chapters.size)
            if (divisionTitle !in expandedDivisions) {
                return@forEach
            }
            chapters.chunked(CHUNK_SIZE).forEachIndexed { index, chunk ->
                val id = groupId(divisionTitle, index)
                built += Row.Group(
                    id = id,
                    title = chunkTitle(chunk),
                    count = chunk.size,
                    chapterIds = chunk.map { it.chapterId },
                )
                if (id in expandedGroups) {
                    chunk.forEach { built += Row.Chapter(it) }
                }
            }
        }
        return built
    }

    private fun initializeExpandedState(candidates: List<ChapterBackupCandidate>) {
        expandedDivisions.clear()
        expandedGroups.clear()
        val divisions = candidates.groupBy { it.displayDivisionTitle() }
        if (divisions.size == 1) {
            val (divisionTitle, chapters) = divisions.entries.first()
            expandedDivisions += divisionTitle
            chapters.chunked(CHUNK_SIZE).forEachIndexed { index, _ ->
                expandedGroups += groupId(divisionTitle, index)
            }
        }
    }

    private fun groupId(divisionTitle: String, index: Int): String {
        return "$divisionTitle#$index"
    }

    private fun chunkTitle(chunk: List<ChapterBackupCandidate>): String {
        val first = chunk.firstOrNull() ?: return "目录段"
        val prefix = if (first.index > 0) "第${first.index}章 " else ""
        return "$prefix${first.title}(共${chunk.size}章)"
    }

    private inner class LoadingAdapter : BaseAdapter() {
        override fun getCount(): Int = 1
        override fun getItem(position: Int): Any = Unit
        override fun getItemId(position: Int): Long = 0
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return TextView(activity).apply {
                text = "正在读取目录..."
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(theme.secondaryText)
                setBackgroundColor(theme.rowBackground)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96))
            }
        }
    }

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun isEnabled(position: Int): Boolean = rows[position] !is Row.Division && rows[position] !is Row.Empty

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return when (val row = rows[position]) {
                is Row.Division -> divisionView(row)
                is Row.Group -> groupView(row)
                is Row.Chapter -> chapterView(row.candidate)
                Row.Empty -> emptyView()
            }
        }
    }

    private fun divisionView(row: Row.Division): View {
        val selectable = candidates.filter { it.displayDivisionTitle() == row.title && it.isExportable() }
        val selectedCount = selectable.count { it.chapterId in selectedIds }
        val allSelected = selectable.isNotEmpty() && selectedCount == selectable.size
        val partial = selectedCount > 0 && !allSelected
        val expanded = row.title in expandedDivisions
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.rowBackground)
            addView(separator(theme.mainBackground), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
            addView(separator(theme.divider), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            addView(
                RelativeLayout(activity).apply {
                    setBackgroundColor(theme.rowBackground)
                    val selectZoneId = View.generateViewId()
                    addView(
                        LinearLayout(activity).apply {
                            id = selectZoneId
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL or Gravity.END
                            isClickable = true
                            setOnClickListener { toggleDivision(row) }
                            addView(
                                TextView(activity).apply {
                                    text = if (selectable.isEmpty()) "不可导出" else "${selectedCount}/${selectable.size}章"
                                    textSize = 12f
                                    typeface = Typeface.DEFAULT_BOLD
                                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                                    setTextColor(theme.accentText)
                                    includeFontPadding = false
                                },
                                LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                                    marginEnd = dp(5)
                                },
                            )
                            addView(checkView(allSelected, selectable.isNotEmpty(), partial), LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT))
                        },
                        RelativeLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            marginEnd = dp(10)
                            addRule(RelativeLayout.ALIGN_PARENT_END)
                        },
                    )
                    addView(
                        LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            isClickable = true
                            setOnClickListener { toggleDivisionExpanded(row) }
                            addView(arrowView(expanded), LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                                marginStart = dp(16)
                            })
                            addView(
                                TextView(activity).apply {
                                    text = row.title
                                    textSize = 15f
                                    setTextColor(theme.sectionText)
                                    maxLines = 1
                                    ellipsize = TextUtils.TruncateAt.END
                                    includeFontPadding = false
                                    gravity = Gravity.CENTER_VERTICAL
                                },
                                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                                    marginStart = dp(12)
                                    marginEnd = dp(10)
                                },
                            )
                        },
                        RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_START)
                            addRule(RelativeLayout.START_OF, selectZoneId)
                        },
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)),
            )
            addView(separator(theme.divider), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))
        }
    }

    private fun groupView(row: Row.Group): View {
        val selectable = row.chapterIds.mapNotNull(candidateById::get).filter { it.isExportable() }
        val selectedCount = selectable.count { it.chapterId in selectedIds }
        val allSelected = selectable.isNotEmpty() && selectedCount == selectable.size
        val partiallySelected = selectedCount > 0 && !allSelected
        val expanded = row.id in expandedGroups
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(40), 0, dp(14), 0)
            setBackgroundColor(theme.rowBackground)

            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    setOnClickListener { toggleGroupExpanded(row) }
                    addView(arrowView(expanded), LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.MATCH_PARENT))
                    addView(
                        TextView(activity).apply {
                            text = row.title
                            textSize = 15f
                            setTextColor(theme.primaryText)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.MIDDLE
                            includeFontPadding = false
                            gravity = Gravity.CENTER_VERTICAL
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                            marginStart = dp(8)
                            marginEnd = dp(10)
                        },
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = dp(10)
                },
            )
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                    isClickable = true
                    setOnClickListener { toggleGroup(row) }
                    addView(
                        TextView(activity).apply {
                            text = if (selectable.isEmpty()) "不可导出" else "${selectedCount}/${selectable.size}章"
                            textSize = 12f
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER_VERTICAL or Gravity.END
                            setTextColor(if (selectedCount > 0) theme.accentText else theme.secondaryText)
                            includeFontPadding = false
                        },
                        LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            marginEnd = dp(5)
                        },
                    )
                    addView(checkView(allSelected, selectable.isNotEmpty(), partiallySelected), LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT))
                },
                LinearLayout.LayoutParams(dp(95), ViewGroup.LayoutParams.MATCH_PARENT),
            )
            layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(49))
        }
    }

    private fun chapterView(candidate: ChapterBackupCandidate): View {
        val enabled = candidate.isExportable()
        val selected = candidate.chapterId in selectedIds
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(64), 0, dp(14), 0)
            setBackgroundColor(theme.catalogBackground)
            isClickable = true
            setOnClickListener { toggle(candidate) }

            addView(
                TextView(activity).apply {
                    text = candidate.title
                    textSize = 13f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(if (enabled) theme.primaryText else theme.tertiaryText)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = dp(10)
                },
            )
            addView(
                TextView(activity).apply {
                    text = when {
                        candidate.cached -> "已缓存"
                        candidate.needsDownload() -> "待下载"
                        else -> "不可导出"
                    }
                    textSize = 13f
                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(
                        when {
                            !enabled -> theme.tertiaryText
                            selected -> theme.accentText
                            else -> theme.secondaryText
                        },
                    )
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(5)
                },
            )
            addView(checkView(selected, enabled), LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT))
            layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(49))
        }
    }

    private fun emptyView(): View {
        return TextView(activity).apply {
            text = "没有读取到章节目录"
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(theme.secondaryText)
            setBackgroundColor(theme.rowBackground)
            layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160))
        }
    }

    private fun arrowView(expanded: Boolean): ImageView {
        return ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER
            val drawable = hostDrawableId(if (expanded) "backmore" else "showmore")
                .takeIf { it != 0 }
                ?: hostDrawableId("showmore")
            if (drawable != 0) {
                setImageResource(drawable)
                ChapterBackupSkinBridge.applyAttr(this, "src", drawable)
            }
            alpha = if (drawable != 0) 1f else 0f
        }
    }

    private fun checkView(selected: Boolean, enabled: Boolean, partial: Boolean = false): View {
        val drawable = when {
            selected || partial -> theme.selectedDrawable
            else -> theme.unselectedDrawable
        }
        if (drawable != 0) {
            return ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER
                setImageResource(drawable)
                ChapterBackupSkinBridge.applyAttr(this, "src", drawable)
                alpha = if (enabled) 1f else 0.35f
            }
        }
        return TextView(activity).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            applyCheckStyle(this, selected, enabled, partial)
        }
    }

    private fun applyCheckStyle(view: TextView, selected: Boolean, enabled: Boolean, partial: Boolean = false) {
        view.text = when {
            !enabled -> ""
            partial -> "–"
            selected -> "✓"
            else -> ""
        }
        view.setTextColor(if (selected || partial) theme.buttonText else theme.titleRightText)
        view.background = when {
            !enabled -> strokeRoundRect(Color.TRANSPARENT, theme.divider, dp(11).toFloat(), 1)
            selected || partial -> roundRect(theme.titleRightText, dp(11).toFloat())
            else -> strokeRoundRect(Color.TRANSPARENT, theme.titleRightText, dp(11).toFloat(), 1)
        }
    }

    private fun separator(color: Int): View {
        return View(activity).apply {
            setBackgroundColor(color)
        }
    }

    private fun roundRect(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun strokeRoundRect(fill: Int, stroke: Int, radius: Float, strokeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(dp(strokeDp), stroke)
        }
    }

    private fun exportButtonBackground(enabled: Boolean): android.graphics.drawable.Drawable {
        val drawableId = if (enabled) theme.buttonDrawable else 0
        if (drawableId != 0) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.resources.getDrawable(drawableId, activity.theme)
            } else {
                @Suppress("DEPRECATION")
                activity.resources.getDrawable(drawableId)
            }
        }
        return roundRect(if (enabled) theme.buttonFallback else theme.buttonDisabled, dp(15).toFloat())
    }

    private fun cursorDrawable(): GradientDrawable {
        return roundRect(NAMING_CURSOR_COLOR, dp(1).toFloat()).apply {
            setSize(dp(2), dp(28))
        }
    }

    private fun hostDrawableId(name: String): Int {
        return hostDrawableId(skinnedNames(name, theme.skinKey))
    }

    private fun hostDrawableId(names: List<String>): Int {
        names.forEach { candidate ->
            val id = activity.resources.getIdentifier(candidate, "drawable", activity.packageName)
            if (id != 0) {
                return id
            }
        }
        return 0
    }

    private fun formatBytes(bytes: Long, selectedCount: Int): String {
        if (selectedCount == 0) {
            return "0 KB"
        }
        if (bytes <= 0L) {
            return "未知"
        }
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.US, "%.1f KB", kb.coerceAtLeast(0.1))
        } else {
            String.format(Locale.US, "%.2f MB", kb / 1024.0)
        }
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density + 0.5f).toInt()
    }

    private sealed class Row {
        data class Division(val title: String, val count: Int) : Row()
        data class Group(val id: String, val title: String, val count: Int, val chapterIds: List<String>) : Row()
        data class Chapter(val candidate: ChapterBackupCandidate) : Row()
        data object Empty : Row()
    }

    data class RestoreState(
        val book: ChapterBackupBook,
        val customBaseName: String?,
        val candidates: List<ChapterBackupCandidate>,
        val selectedIds: List<String>,
        val expandedDivisions: List<String>,
        val expandedGroups: List<String>,
        val firstVisiblePosition: Int,
        val createdAtMillis: Long,
    ) {
        val hasCandidates: Boolean
            get() = candidates.isNotEmpty()

        fun isFresh(): Boolean {
            return System.currentTimeMillis() - createdAtMillis <= RESTORE_TTL_MS
        }

        companion object {
            fun capture(window: ChapterExportSelectionWindow): RestoreState? {
                val currentBook = window.book ?: return null
                return RestoreState(
                    book = currentBook,
                    customBaseName = window.customBaseName,
                    candidates = window.candidates,
                    selectedIds = window.selectedIds.toList(),
                    expandedDivisions = window.expandedDivisions.toList(),
                    expandedGroups = window.expandedGroups.toList(),
                    firstVisiblePosition = window.firstVisiblePositionOrZero(),
                    createdAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    private data class HostPageTheme(
        val skinKey: String,
        val mainBackground: Int,
        val rowBackground: Int,
        val catalogBackground: Int,
        val titleBackground: Int,
        val titleText: Int,
        val titleRightText: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val tertiaryText: Int,
        val sectionText: Int,
        val accentText: Int,
        val divider: Int,
        val buttonText: Int,
        val buttonFallback: Int,
        val buttonDisabled: Int,
        val selectedDrawable: Int,
        val unselectedDrawable: Int,
        val buttonDrawable: Int,
    ) {
        companion object {
            fun from(context: Context): HostPageTheme {
                val skinKey = currentSkinKey(context)
                val night = skinKey == "night"
                val neutralMain = ChapterBackupSkinBridge.neutralBackground(night)
                val neutralPanel = ChapterBackupSkinBridge.neutralPanel(night)
                val primary = hostColor(context, "textTitle", if (night) 0xFFCCCCCC.toInt() else Color.BLACK)
                val secondary = hostColor(context, "text_666", if (night) 0xFF999999.toInt() else 0xFF666666.toInt())
                val titleRight = hostColor(context, "color_title_textright", primary)
                val accent = hostColor(context, "text_base_color", titleRight)
                return HostPageTheme(
                    skinKey = skinKey,
                    mainBackground = hostColor(context, "color_bg_main", neutralMain),
                    rowBackground = hostColor(context, "color_bg_1", neutralPanel),
                    catalogBackground = hostColor(context, "color_bg_catalog", neutralMain),
                    titleBackground = hostColor(context, "color_title_bg1", neutralPanel),
                    titleText = hostColor(context, "color_title_text1", primary),
                    titleRightText = titleRight,
                    primaryText = primary,
                    secondaryText = secondary,
                    tertiaryText = if (night) 0xFF777777.toInt() else 0xFF999999.toInt(),
                    sectionText = hostColor(context, "text_3797cc", primary),
                    accentText = accent,
                    divider = hostColor(context, "divider", if (night) 0xFF161616.toInt() else 0xFFE6E6E6.toInt()),
                    buttonText = hostColor(context, "btn_cumText", if (night) Color.BLACK else Color.WHITE),
                    buttonFallback = hostColor(context, "color_base", accent),
                    buttonDisabled = if (night) 0xFF4C4C4C.toInt() else 0xFFDDDDDD.toInt(),
                    selectedDrawable = hostDrawableId(context, "selected"),
                    unselectedDrawable = hostDrawableId(context, "select").takeIf { it != 0 }
                        ?: hostDrawableId(context, "select_grey"),
                    buttonDrawable = 0,
                )
            }

            private fun currentSkinKey(context: Context): String {
                val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val followSystem = settings.getBoolean("IsfollowNight", false)
                val isNight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && followSystem) {
                    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
                } else {
                    context.getSharedPreferences(CiweiMaoPackages.DEFAULT_PREF, Context.MODE_PRIVATE)
                        .getBoolean("isNight", false)
                }
                return if (isNight) {
                    "night"
                } else {
                    settings.getString("skinType", "yellow") ?: "yellow"
                }
            }

            private fun hostColor(context: Context, name: String, fallback: Int): Int {
                return ChapterBackupSkinBridge.color(context, name, fallback)
            }

            private fun hostDrawableId(context: Context, name: String): Int {
                return ChapterBackupSkinBridge.drawableId(context, name)
            }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 10
        private const val TAG = "CWMHook.ChapterExport"
        private const val PAGE_ANIM_MS = 320L
        private const val EXPAND_COLLAPSE_ANIM_MS = 280L
        private const val EXPAND_COLLAPSE_STAGGER_MS = 10L
        private const val EXPAND_COLLAPSE_MAX_STAGGER_MS = 60L
        private const val RESTORE_TTL_MS = 12_000L
        private const val NAMING_SELECTION_COLOR = 0x6633B5E5
        private const val NAMING_CURSOR_COLOR = 0xFF33B5E5.toInt()
        private val activeWindows = LinkedHashSet<ChapterExportSelectionWindow>()
        private var pendingRestore: RestoreState? = null

        private fun registerWindow(window: ChapterExportSelectionWindow) {
            synchronized(activeWindows) {
                activeWindows += window
            }
        }

        private fun unregisterWindow(window: ChapterExportSelectionWindow) {
            synchronized(activeWindows) {
                activeWindows -= window
            }
        }

        fun scheduleHostSkinRefresh(reason: String) {
            val windows = synchronized(activeWindows) { activeWindows.toList() }
            if (windows.isEmpty()) {
                return
            }
            windows.forEach { it.scheduleThemeRefresh(reason) }
        }

        fun restoreIfNeeded(
            activity: Activity,
            exporter: ChapterBackupExporter,
            bookInfo: Any?,
            downloadType: String?,
        ): Boolean {
            val currentBook = bookInfo?.toChapterBackupBook()
            val state = synchronized(activeWindows) {
                if (activeWindows.isNotEmpty()) {
                    return false
                }
                val pending = pendingRestore ?: return false
                if (!pending.isFresh()) {
                    pendingRestore = null
                    return false
                }
                if (currentBook != null && currentBook.bookId != pending.book.bookId) {
                    return false
                }
                pendingRestore = null
                pending
            }
            activity.window.decorView.postDelayed(
                {
                    if (!activity.isFinishing) {
                        ChapterExportSelectionWindow(activity, exporter, bookInfo, downloadType, state).show()
                        ModuleFileLogger.i(TAG, "Chapter export selector restored after host recreate: book=${state.book.bookId}, loaded=${state.hasCandidates}")
                    }
                },
                180L,
            )
            return true
        }
    }
}

private fun skinnedNames(name: String, skinKey: String): List<String> {
    return when (skinKey) {
        "night" -> listOf("${name}_night", name)
        "green", "pink" -> listOf("${name}_$skinKey", name)
        else -> listOf(name)
    }
}

private fun isLightColor(color: Int): Boolean {
    val red = Color.red(color) / 255.0
    val green = Color.green(color) / 255.0
    val blue = Color.blue(color) / 255.0
    return 0.299 * red + 0.587 * green + 0.114 * blue > 0.62
}

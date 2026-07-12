package com.xiyunmn.cwmhook.feature.readerfont

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.Gravity
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfigStore
import com.xiyunmn.cwmhook.core.hostui.HostSkinResolver
import com.xiyunmn.cwmhook.core.icons.CommonIconPainter
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import java.io.File
import java.util.Locale
import kotlin.math.min

internal class ReaderFontSettingsInjector(
    private val typefaceProvider: ReaderFontTypefaceProvider,
    private val logTag: String,
) {
    companion object {
        const val REQUEST_IMPORT_FONT = 0x434D464E
    }

    private val panelTag = "cwmhook_reader_font_import_panel"
    private val scrollTag = "cwmhook_reader_font_scroll"
    private val scrollContentTag = "cwmhook_reader_font_scroll_content"
    private val customRowTagPrefix = "cwmhook_reader_font_row:"
    private val customCheckTagPrefix = "cwmhook_reader_font_check:"
    private val manageRowTagPrefix = "cwmhook_reader_font_manage_row:"

    private data class HostPalette(
        val skinKey: String,
        val rowBackground: Int,
        val titleText: Int,
        val subText: Int,
        val accent: Int,
    )

    fun inject(activity: Activity, activityClass: Class<*>) {
        runCatching {
            if (!typefaceProvider.isEnabled(activity)) {
                removeInjectedViews(activity)
                return@runCatching
            }
            val contentRoot = ensureSettingsScrollable(activity) ?: contentLinearRoot(activity) ?: return@runCatching
            extendFontSettingsScrollRange(activity, contentRoot)
            insertImportPanel(activity, activityClass, contentRoot)
            rebuildCustomRows(activity, activityClass)
            updateCustomChecks(activity)
        }.onFailure { throwable ->
            ModuleFileLogger.e(logTag, "Failed to inject reader font settings", throwable)
        }
    }

    fun handleActivityResult(
        activity: Activity,
        activityClass: Class<*>,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        return handleImportResult(activity, requestCode, resultCode, data) { imported ->
            applyCustomFontSelection(activity, activityClass, imported.first())
            rebuildCustomRows(activity, activityClass)
            updateCustomChecks(activity)
        }
    }

    fun handleImportResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onImported: (List<String>) -> Unit = {},
    ): Boolean {
        if (requestCode != REQUEST_IMPORT_FONT) {
            return false
        }
        if (!typefaceProvider.isEnabled(activity)) {
            Toast.makeText(activity, "阅读页字体自定义未启用", Toast.LENGTH_SHORT).show()
            return true
        }
        if (resultCode != Activity.RESULT_OK) {
            return true
        }
        val result = importSelectedFonts(activity, data)
        if (result.imported.isNotEmpty()) {
            onImported(result.imported)
        }
        return true
    }

    fun updateCustomChecks(activity: Activity) {
        if (!typefaceProvider.isEnabled(activity)) {
            removeInjectedViews(activity)
            return
        }
        val currentPath = typefaceProvider.currentTextTypePath(activity)
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        traverse(root) { view ->
            val tag = view.tag as? String ?: return@traverse
            if (tag.startsWith(customCheckTagPrefix)) {
                view.visibility = if (sameFontPath(tag.removePrefix(customCheckTagPrefix), currentPath)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }

    fun updateReaderSettingLabel(layout: Any?) {
        val view = layout as? View ?: return
        val context = view.context ?: return
        if (!typefaceProvider.isEnabled(context)) {
            return
        }
        val path = typefaceProvider.currentTextTypePath(context)
        if (!typefaceProvider.isCustomFontPath(path)) {
            return
        }
        val id = context.resources.getIdentifier("texttype_tv", "id", context.packageName)
        val textView = view.findViewById<TextView>(id) ?: return
        val displayName = readerMenuDisplayName(typefaceProvider.displayName(path))
        textView.text = displayName
        if (isLongAsciiName(displayName)) {
            textView.textSize = 14f
        }
        textView.maxLines = 1
        textView.ellipsize = TextUtils.TruncateAt.END
        textView.includeFontPadding = false
        typefaceProvider.load(path)?.let { textView.typeface = it }
        constrainReaderMenuFontLabel(view, textView)
    }

    private fun insertImportPanel(activity: Activity, activityClass: Class<*>, root: LinearLayout) {
        if (root.findViewWithTag<View>(panelTag) != null) {
            return
        }
        val palette = hostPalette(activity)
        val buttonBackground = skinnedButtonDrawableId(activity, palette.skinKey)

        val panel = LinearLayout(activity).apply {
            tag = panelTag
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10))
            setBackgroundColor(palette.rowBackground)
        }
        panel.addView(TextView(activity).apply {
            text = "自定义字体"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.titleText)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(TextView(activity).apply {
            text = "通过系统文件选择器导入 .ttf / .otf / .ttc，文件会复制到刺猬猫私有目录"
            textSize = 12f
            setTextColor(palette.subText)
            setPadding(0, dp(activity, 5), 0, dp(activity, 9))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    createPanelButton(activity, "导入字体文件", palette, buttonBackground) {
                        startFontPicker(activity)
                    },
                    LinearLayout.LayoutParams(0, dp(activity, 42), 1f),
                )
                addView(
                    createPanelButton(activity, "管理本地字体", palette, buttonBackground) {
                        showFontManager(activity, activityClass)
                    },
                    LinearLayout.LayoutParams(0, dp(activity, 42), 1f).apply {
                        leftMargin = dp(activity, 10)
                    },
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        val insertIndex = if (root.tag == scrollContentTag) 0 else 2.coerceAtMost(root.childCount)
        root.addView(panel, insertIndex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun createPanelButton(
        activity: Activity,
        label: String,
        palette: HostPalette,
        backgroundId: Int,
        action: () -> Unit,
    ): TextView {
        return TextView(activity).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            if (backgroundId != 0) {
                setBackgroundResource(backgroundId)
            } else {
                background = rounded(palette.accent, dp(activity, 8).toFloat())
            }
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                action()
            }
        }
    }

    fun startFontPicker(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "font/collection",
                    "application/x-font-ttf",
                    "application/x-font-otf",
                    "application/vnd.ms-opentype",
                    "application/octet-stream",
                ),
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_IMPORT_FONT)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "无法打开系统文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private data class FontImportResult(
        val imported: List<String>,
        val failedCount: Int,
    )

    private fun importSelectedFonts(activity: Activity, data: Intent?): FontImportResult {
        val uris = selectedFontUris(data)
        if (uris.isEmpty()) {
            Toast.makeText(activity, "未读取到字体文件", Toast.LENGTH_SHORT).show()
            return FontImportResult(emptyList(), 0)
        }

        return runCatching {
            val imported = mutableListOf<String>()
            var failedCount = 0
            uris.forEach { uri ->
                runCatching {
                    val font = copyFontToPrivateDir(activity, uri)
                    if (typefaceProvider.validate(font.absolutePath)) {
                        imported += font.absolutePath
                        ModuleFileLogger.i(logTag, "Imported reader font: ${font.absolutePath}")
                    } else {
                        font.delete()
                        failedCount++
                    }
                }.onFailure { throwable ->
                    failedCount++
                    ModuleFileLogger.e(logTag, "Failed to import one reader font", throwable)
                }
            }
            if (imported.isEmpty()) {
                Toast.makeText(activity, "字体文件无法加载", Toast.LENGTH_SHORT).show()
                FontImportResult(emptyList(), failedCount)
            } else {
                ReaderFontConfigStore.rememberFonts(activity, imported)
                val message = if (failedCount == 0) {
                    "已导入 ${imported.size} 个字体"
                } else {
                    "已导入 ${imported.size} 个字体，${failedCount} 个失败"
                }
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                FontImportResult(imported, failedCount)
            }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(logTag, "Failed to import reader font", throwable)
            Toast.makeText(activity, "导入字体失败", Toast.LENGTH_SHORT).show()
            FontImportResult(emptyList(), 0)
        }
    }

    private fun selectedFontUris(data: Intent?): List<Uri> {
        return buildList {
            data?.data?.let { add(it) }
            val clipData = data?.clipData
            if (clipData != null) {
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index)?.uri?.let { add(it) }
                }
            }
        }.distinctBy { it.toString() }
    }

    private fun copyFontToPrivateDir(activity: Activity, uri: Uri): File {
        val name = ensureFontExtension(
            sanitizeFileName(queryDisplayName(activity, uri) ?: "font_${System.currentTimeMillis()}.ttf"),
            activity.contentResolver.getType(uri),
        )
        val dir = File(activity.filesDir, "cwmhook/fonts").apply { mkdirs() }
        val target = uniqueFile(dir, name)
        val input = activity.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("openInputStream returned null")
        input.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
        return target
    }

    private fun queryDisplayName(activity: Activity, uri: Uri): String? {
        return activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
            .trim()
            .trim('.', ' ')
        return cleaned.ifBlank { "font_${System.currentTimeMillis()}.ttf" }
    }

    private fun ensureFontExtension(name: String, mimeType: String?): String {
        if (typefaceProvider.isFontFile(name)) {
            return name
        }
        val extension = when (mimeType?.lowercase(Locale.US)) {
            "font/otf", "application/x-font-otf", "application/vnd.ms-opentype" -> ".otf"
            "font/collection" -> ".ttc"
            else -> ".ttf"
        }
        return name.substringBeforeLast('.', name) + extension
    }

    private fun uniqueFile(dir: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) {
            val suffix = if (ext.isBlank()) "_$index" else "_$index.$ext"
            candidate = File(dir, base + suffix)
            index++
        }
        return candidate
    }

    private fun rebuildCustomRows(activity: Activity, activityClass: Class<*>) {
        val jianLay = findHostViewByName<LinearLayout>(activity, "lay_jian")
        val fanLay = findHostViewByName<LinearLayout>(activity, "lay_fan")
        listOfNotNull(jianLay, fanLay).forEach { removeExistingCustomRows(it) }

        ReaderFontConfigStore.readFonts(activity)
            .filter { typefaceProvider.isCustomFontPath(it) && File(it).isFile }
            .forEach { path ->
                jianLay?.addView(createCustomFontRow(activity, activityClass, path), customRowParams(activity))
                fanLay?.addView(createCustomFontRow(activity, activityClass, path), customRowParams(activity))
            }
    }

    private fun createCustomFontRow(activity: Activity, activityClass: Class<*>, path: String): View {
        val palette = hostPalette(activity)
        val row = RelativeLayout(activity).apply {
            tag = customRowTagPrefix + path
            setBackgroundColor(palette.rowBackground)
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                selectCustomFont(activity, activityClass, path)
            }
        }
        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 16), 0, dp(activity, 52), 0)
        }
        textColumn.addView(TextView(activity).apply {
            text = typefaceProvider.displayName(path)
            textSize = 18f
            includeFontPadding = false
            setTextColor(palette.titleText)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typefaceProvider.load(path)?.let { typeface = it }
        })
        textColumn.addView(TextView(activity).apply {
            text = path
            textSize = 12f
            includeFontPadding = false
            setTextColor(palette.subText)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(0, dp(activity, 5), 0, 0)
        })
        row.addView(
            textColumn,
            RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.CENTER_VERTICAL)
            },
        )
        val selectedDrawable = skinnedDrawableId(activity, "selected", palette.skinKey)
        row.addView(
            ImageView(activity).apply {
                tag = customCheckTagPrefix + path
                visibility = View.GONE
                if (selectedDrawable != 0) {
                    setImageResource(selectedDrawable)
                }
            },
            RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
                rightMargin = dp(activity, 16)
            },
        )
        return row
    }

    private fun showFontManager(activity: Activity, activityClass: Class<*>) {
        if (managedFontPaths(activity).isEmpty()) {
            Toast.makeText(activity, "暂无已添加字体", Toast.LENGTH_SHORT).show()
            return
        }
        val palette = hostPalette(activity)
        val metrics = activity.resources.displayMetrics
        val popupWidth = (metrics.widthPixels - dp(activity, 32)).coerceAtMost(dp(activity, 460))
        val popupHeight = (metrics.heightPixels * 7 / 10).coerceAtMost(dp(activity, 560))
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 14))
            background = rounded(palette.rowBackground, dp(activity, 10).toFloat())
        }
        content.addView(TextView(activity).apply {
            text = "管理本地字体"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.titleText)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(TextView(activity).apply {
            text = "点击切换字体，长按拖动排序；删除仅移除本地副本"
            textSize = 12f
            setTextColor(palette.subText)
            setPadding(0, dp(activity, 4), 0, dp(activity, 10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        lateinit var popup: PopupWindow
        fun renderList() {
            list.removeAllViews()
            managedFontPaths(activity).forEach { path ->
                list.addView(
                    createManagerRow(
                        activity = activity,
                        path = path,
                        palette = palette,
                        onSelect = {
                            selectCustomFont(activity, activityClass, path)
                            renderList()
                        },
                        onDelete = {
                            deleteManagedFont(activity, activityClass, path)
                            if (managedFontPaths(activity).isEmpty()) {
                                popup.dismiss()
                            } else {
                                renderList()
                            }
                        },
                    ),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 64)).apply {
                        bottomMargin = dp(activity, 8)
                    },
                )
            }
        }
        val scrollView = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        val doneButton = TextView(activity).apply {
            text = "完成"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            val backgroundId = skinnedButtonDrawableId(activity, palette.skinKey)
            if (backgroundId != 0) {
                setBackgroundResource(backgroundId)
            } else {
                background = rounded(palette.accent, dp(activity, 8).toFloat())
            }
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                popup.dismiss()
            }
        }
        content.addView(doneButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42)).apply {
            topMargin = dp(activity, 8)
        })
        attachManagerDragListener(activity, activityClass, list, scrollView, content, doneButton)

        popup = PopupWindow(content, popupWidth, popupHeight, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(activity, 10).toFloat()
        }
        renderList()
        popup.showAtLocation(activity.window.decorView, Gravity.CENTER, 0, 0)
    }

    private fun createManagerRow(
        activity: Activity,
        path: String,
        palette: HostPalette,
        onSelect: () -> Unit,
        onDelete: () -> Unit,
    ): View {
        val file = File(path)
        val selected = sameFontPath(typefaceProvider.currentTextTypePath(activity), path)
        val row = LinearLayout(activity).apply {
            tag = manageRowTagPrefix + path
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 10), 0, dp(activity, 8), 0)
            background = rounded(
                if (selected) blendColor(palette.rowBackground, palette.accent, 0.10f) else palette.rowBackground,
                dp(activity, 8).toFloat(),
            )
            isLongClickable = true
            setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                startManagerDrag(view)
            }
            setOnClickListener {
                if (file.isFile) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSelect()
                }
            }
        }
        row.addView(
            ReaderFontIconView(
                activity,
                ReaderFontIconKind.DRAG,
                if (selected) palette.accent else palette.subText,
            ),
            LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )

        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 8), 0, dp(activity, 8), 0)
        }
        texts.addView(TextView(activity).apply {
            text = typefaceProvider.displayName(path)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (selected) palette.accent else palette.titleText)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typefaceProvider.load(path)?.let { typeface = it }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        texts.addView(TextView(activity).apply {
            text = when {
                !file.isFile -> "文件不存在"
                selected -> "当前使用 · $path"
                else -> path
            }
            textSize = 11f
            setTextColor(if (selected) palette.accent else palette.subText)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(0, dp(activity, 4), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            RelativeLayout(activity).apply {
                if (selected) {
                    addView(
                        ReaderFontIconView(activity, ReaderFontIconKind.RADIO_SELECTED, palette.accent).apply {
                            contentDescription = "当前使用"
                        },
                        RelativeLayout.LayoutParams(dp(activity, 34), dp(activity, 34)).apply {
                            addRule(RelativeLayout.CENTER_IN_PARENT)
                        },
                    )
                }
            },
            LinearLayout.LayoutParams(dp(activity, 42), ViewGroup.LayoutParams.MATCH_PARENT),
        )
        row.addView(
            RelativeLayout(activity).apply {
                isClickable = true
                addView(
                    ReaderFontIconView(activity, ReaderFontIconKind.DELETE, palette.accent),
                    RelativeLayout.LayoutParams(dp(activity, 36), dp(activity, 36)).apply {
                        addRule(RelativeLayout.CENTER_IN_PARENT)
                    },
                )
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onDelete()
                }
            },
            LinearLayout.LayoutParams(dp(activity, 50), ViewGroup.LayoutParams.MATCH_PARENT),
        )
        return row
    }

    private fun startManagerDrag(view: View): Boolean {
        val tag = view.tag as? String ?: return false
        view.alpha = 0.55f
        val clip = ClipData.newPlainText("reader-font", tag.removePrefix(manageRowTagPrefix))
        val shadow = View.DragShadowBuilder(view)
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clip, shadow, view, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(clip, shadow, view, 0)
        }
        if (!started) {
            view.alpha = 1f
        }
        return started
    }

    private fun attachManagerDragListener(
        activity: Activity,
        activityClass: Class<*>,
        list: LinearLayout,
        scrollView: ScrollView,
        vararg extraTargets: View,
    ) {
        var orderSaved = false
        val listener = View.OnDragListener dragListener@{ receiver, event ->
            val dragged = event.localState as? View ?: return@dragListener true
            val tag = dragged.tag as? String
            if (tag?.startsWith(manageRowTagPrefix) != true) {
                return@dragListener false
            }
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    orderSaved = false
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    autoScrollManagerList(scrollView, receiver, event.y)
                    moveDraggedManagerRow(list, dragged, dragYInView(receiver, list, event.y))
                    true
                }
                DragEvent.ACTION_DROP -> true
                DragEvent.ACTION_DRAG_ENDED -> {
                    dragged.alpha = 1f
                    if (!orderSaved) {
                        saveManagerOrder(activity, activityClass, list)
                        orderSaved = true
                    }
                    true
                }
                else -> true
            }
        }
        list.setOnDragListener(listener)
        scrollView.setOnDragListener(listener)
        extraTargets.forEach { it.setOnDragListener(listener) }
    }

    private fun autoScrollManagerList(scrollView: ScrollView, receiver: View, receiverY: Float) {
        val localY = dragYInView(receiver, scrollView, receiverY)
        val edge = dp(scrollView.context, 56).coerceAtMost(scrollView.height / 2)
        val delta = when {
            localY < edge -> -dp(scrollView.context, 4)
            localY > scrollView.height - edge -> dp(scrollView.context, 4)
            else -> 0
        }
        if (delta != 0) {
            scrollView.scrollBy(0, delta)
        }
    }

    private fun dragYInView(receiver: View, target: View, receiverY: Float): Float {
        val receiverLocation = IntArray(2)
        val targetLocation = IntArray(2)
        receiver.getLocationOnScreen(receiverLocation)
        target.getLocationOnScreen(targetLocation)
        return receiverY + receiverLocation[1] - targetLocation[1]
    }

    private fun moveDraggedManagerRow(list: LinearLayout, dragged: View, y: Float) {
        val currentIndex = list.indexOfChild(dragged)
        if (currentIndex < 0) {
            return
        }
        var targetIndex = list.childCount
        for (index in 0 until list.childCount) {
            val child = list.getChildAt(index)
            if (child == dragged) {
                continue
            }
            if (y < child.top + child.height / 2f) {
                targetIndex = index
                break
            }
        }
        if (targetIndex > currentIndex) {
            targetIndex--
        }
        if (targetIndex == currentIndex) {
            return
        }
        list.removeView(dragged)
        list.addView(dragged, targetIndex.coerceIn(0, list.childCount))
    }

    private fun saveManagerOrder(activity: Activity, activityClass: Class<*>, list: LinearLayout) {
        val ordered = buildList {
            for (index in 0 until list.childCount) {
                val tag = list.getChildAt(index).tag as? String ?: continue
                if (tag.startsWith(manageRowTagPrefix)) {
                    add(tag.removePrefix(manageRowTagPrefix))
                }
            }
        }
        if (ordered.isNotEmpty()) {
            ReaderFontConfigStore.writeFonts(activity, ordered)
            rebuildCustomRows(activity, activityClass)
            updateCustomChecks(activity)
        }
    }

    private fun deleteManagedFont(activity: Activity, activityClass: Class<*>, path: String) {
        val remaining = ReaderFontConfigStore.readFonts(activity).filterNot { sameFontPath(it, path) }
        ReaderFontConfigStore.writeFonts(activity, remaining)
        deletePrivateFontCopy(activity, path)
        if (sameFontPath(typefaceProvider.currentTextTypePath(activity), path)) {
            resetCurrentFont(activity, activityClass)
        }
        rebuildCustomRows(activity, activityClass)
        updateCustomChecks(activity)
        Toast.makeText(activity, "已删除 ${typefaceProvider.displayName(path)}", Toast.LENGTH_SHORT).show()
    }

    private fun managedFontPaths(activity: Activity): List<String> {
        return ReaderFontConfigStore.readFonts(activity)
            .filter { typefaceProvider.isCustomFontPath(it) }
    }

    private fun deletePrivateFontCopy(context: Context, path: String) {
        runCatching {
            val fontsDir = File(context.filesDir, "cwmhook/fonts").canonicalFile
            val file = File(path).canonicalFile
            if (file.path.startsWith(fontsDir.path + File.separator) && file.isFile) {
                file.delete()
            }
        }.onFailure { throwable ->
            ModuleFileLogger.e(logTag, "Failed to delete private font copy: $path", throwable)
        }
    }

    private fun resetCurrentFont(activity: Activity, activityClass: Class<*>) {
        val textType = typefaceProvider.currentTextType(activity)
        activity.getSharedPreferences("front", Activity.MODE_PRIVATE)
            .edit()
            .putString("textTypePath1", "syht.otf")
            .putString("textType1", textType)
            .commit()
        setPrivateString(activity, activityClass, "newPathType", "syht.otf")
        setPrivateString(activity, activityClass, "newTxtType", textType)
        setResultChanged(activity, activityClass)
        invokePrivateNoArg(activity, activityClass, "setChoseImage")
    }

    private fun selectCustomFont(activity: Activity, activityClass: Class<*>, path: String) {
        applyCustomFontSelection(activity, activityClass, path)
        updateCustomChecks(activity)
    }

    private fun applyCustomFontSelection(activity: Activity, activityClass: Class<*>, path: String) {
        val textType = typefaceProvider.currentTextType(activity)
        activity.getSharedPreferences("front", Activity.MODE_PRIVATE)
            .edit()
            .putString("textTypePath1", path)
            .putString("textType1", textType)
            .commit()
        setPrivateString(activity, activityClass, "newPathType", path)
        setPrivateString(activity, activityClass, "newTxtType", textType)
        setResultChanged(activity, activityClass)
        invokePrivateNoArg(activity, activityClass, "setChoseImage")
    }

    private fun ensureSettingsScrollable(activity: Activity): LinearLayout? {
        val root = contentLinearRoot(activity) ?: return null
        (root.findViewWithTag<View>(scrollContentTag) as? LinearLayout)?.let { return it }
        if (root.findViewWithTag<View>(scrollTag) != null) {
            return null
        }

        val firstScrollableIndex = firstFontContentIndex(activity, root)
        if (firstScrollableIndex !in 0 until root.childCount) {
            return root
        }

        val content = LinearLayout(activity).apply {
            tag = scrollContentTag
            orientation = LinearLayout.VERTICAL
        }
        while (root.childCount > firstScrollableIndex) {
            val child = root.getChildAt(firstScrollableIndex)
            val params = child.layoutParams
            root.removeViewAt(firstScrollableIndex)
            content.addView(child, params)
        }

        val scroll = ScrollView(activity).apply {
            tag = scrollTag
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                content,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        root.addView(
            scroll,
            firstScrollableIndex,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        return content
    }

    private fun extendFontSettingsScrollRange(activity: Activity, content: LinearLayout) {
        if (content.tag != scrollContentTag) {
            return
        }
        val bottomPadding = dp(activity, 48)
        if (content.paddingBottom < bottomPadding) {
            content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, bottomPadding)
        }
    }

    private fun firstFontContentIndex(activity: Activity, root: LinearLayout): Int {
        val indices = listOf("lay_jian", "lay_fan").mapNotNull { name ->
            val id = activity.resources.getIdentifier(name, "id", activity.packageName)
            if (id == 0) {
                null
            } else {
                val child = root.findViewById<View>(id)
                root.indexOfChild(child).takeIf { it >= 0 }
            }
        }
        return indices.minOrNull() ?: 0
    }

    private fun contentLinearRoot(activity: Activity): LinearLayout? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return content.getChildAt(0) as? LinearLayout
    }

    private fun removeInjectedViews(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        removeTaggedChildren(root)
    }

    private fun removeTaggedChildren(parent: ViewGroup) {
        for (index in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(index)
            val tag = child.tag as? String
            if (tag == panelTag || tag?.startsWith(customRowTagPrefix) == true) {
                parent.removeViewAt(index)
            } else if (child is ViewGroup) {
                removeTaggedChildren(child)
            }
        }
    }

    private fun removeExistingCustomRows(container: ViewGroup) {
        for (index in container.childCount - 1 downTo 0) {
            val tag = container.getChildAt(index).tag as? String
            if (tag?.startsWith(customRowTagPrefix) == true) {
                container.removeViewAt(index)
            }
        }
    }

    private fun customRowParams(activity: Activity): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 60)).apply {
            topMargin = dp(activity, 1)
        }
    }

    private fun <T : View> findHostViewByName(activity: Activity, name: String): T? {
        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
        if (id == 0) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return activity.findViewById<View>(id) as? T
    }

    private fun hostPalette(context: Context): HostPalette {
        val skinKey = HostSkinResolver.currentSkinKey(context)
        val night = skinKey == "night"
        return HostPalette(
            skinKey = skinKey,
            rowBackground = HostSkinResolver.skinnedColor(context, "color_bg_1", skinKey, if (night) Color.rgb(53, 53, 53) else Color.WHITE),
            titleText = HostSkinResolver.skinnedColor(context, "textTitle", skinKey, if (night) Color.rgb(204, 204, 204) else Color.BLACK),
            subText = if (night) Color.rgb(138, 138, 138) else 0xFF828282.toInt(),
            accent = if (night) {
                HostSkinResolver.color(context, "color_base_night", Color.rgb(88, 88, 88))
            } else {
                HostSkinResolver.skinnedColor(context, "color_base", skinKey, 0xFFF9BE00.toInt())
            },
        )
    }

    private fun skinnedDrawableId(context: Context, name: String, skinKey: String): Int {
        return HostSkinResolver.skinnedDrawableId(context, name, skinKey)
    }

    private fun skinnedButtonDrawableId(context: Context, skinKey: String): Int {
        return if (skinKey == "night") {
            HostSkinResolver.drawableId(context, "btn_base_round5_night")
        } else {
            HostSkinResolver.skinnedDrawableId(context, "btn_base_round5", skinKey)
        }
    }

    private fun constrainReaderMenuFontLabel(root: View, textView: TextView) {
        val context = textView.context
        textView.maxWidth = dp(context, 116)
        root.post {
            val selector = textView.parent as? ViewGroup ?: return@post
            val parentRow = selector.parent as? ViewGroup ?: return@post
            val sizeControls = parentRow.findViewById<View>(
                root.resources.getIdentifier("lay1", "id", root.context.packageName),
            )
            val rowWidth = parentRow.width - parentRow.paddingLeft - parentRow.paddingRight
            if (rowWidth <= 0) {
                return@post
            }
            compactReaderMenuSizeControls(sizeControls, rowWidth)
            val usedBySizeControls = sizeControls?.let { measuredWidthWithMargins(it) } ?: 0
            val selectorMaxWidth = (rowWidth - usedBySizeControls - dp(context, 2))
                .coerceIn(dp(context, 72), dp(context, 176))
            val textMaxWidth = (selectorMaxWidth - trailingChildrenWidth(selector, textView))
                .coerceAtLeast(dp(context, 40))
            textView.maxWidth = textMaxWidth
            textView.requestLayout()
        }
    }

    private fun readerMenuDisplayName(name: String): String {
        return name.trim()
            .replace(Regex("""(?i)[\s_-]+(regular|normal|book)$"""), "")
            .ifBlank { name }
    }

    private fun isLongAsciiName(name: CharSequence): Boolean {
        if (name.length < 12) {
            return false
        }
        val asciiCount = name.count { it.code in 33..126 }
        return asciiCount * 3 >= name.length * 2
    }

    private fun measuredWidthWithMargins(view: View): Int {
        val margins = view.layoutParams as? ViewGroup.MarginLayoutParams
        val width = when {
            margins != null && margins.width > 0 -> margins.width
            view.width > 0 -> view.width
            else -> view.measuredWidth
        }
        return width + (margins?.leftMargin ?: 0) + (margins?.rightMargin ?: 0)
    }

    private fun compactReaderMenuSizeControls(sizeControls: View?, rowWidth: Int) {
        if (sizeControls == null) {
            return
        }
        val params = sizeControls.layoutParams ?: return
        val targetWidth = (rowWidth * 54 / 100).coerceIn(
            dp(sizeControls.context, 188),
            dp(sizeControls.context, 204),
        )
        if (params.width > targetWidth) {
            params.width = targetWidth
            sizeControls.layoutParams = params
        }
    }

    private fun trailingChildrenWidth(parent: ViewGroup, main: View): Int {
        var width = 0
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child != main && child.visibility != View.GONE) {
                width += measuredWidthWithMargins(child)
            }
        }
        return width.coerceAtLeast(dp(parent.context, 24))
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun blendColor(base: Int, overlay: Int, fraction: Float): Int {
        val amount = fraction.coerceIn(0f, 1f)
        val baseAmount = 1f - amount
        return Color.rgb(
            (Color.red(base) * baseAmount + Color.red(overlay) * amount).toInt(),
            (Color.green(base) * baseAmount + Color.green(overlay) * amount).toInt(),
            (Color.blue(base) * baseAmount + Color.blue(overlay) * amount).toInt(),
        )
    }

    private fun sameFontPath(first: String, second: String): Boolean {
        if (first.isBlank() || second.isBlank()) {
            return first == second
        }
        fun normalized(path: String): String {
            return runCatching { File(path).canonicalPath }
                .getOrElse { File(path).absolutePath }
        }
        return normalized(first) == normalized(second)
    }

    private fun setPrivateString(target: Any, owner: Class<*>, fieldName: String, value: String) {
        runCatching {
            owner.getDeclaredField(fieldName).also { it.isAccessible = true }.set(target, value)
        }
    }

    private fun invokePrivateNoArg(target: Any, owner: Class<*>, methodName: String) {
        runCatching {
            owner.getDeclaredMethod(methodName).also { it.isAccessible = true }.invoke(target)
        }
    }

    private fun setResultChanged(activity: Activity, activityClass: Class<*>) {
        val resultCode = runCatching {
            activityClass.getDeclaredField("CHANGE_TEXTTYPE").also { it.isAccessible = true }.getInt(null)
        }.getOrDefault(12345)
        activity.setResult(resultCode)
    }

    private fun traverse(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                traverse(view.getChildAt(index), action)
            }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    private enum class ReaderFontIconKind {
        DRAG,
        DELETE,
        RADIO_SELECTED,
    }

    private class ReaderFontIconView(
        context: Context,
        private val kind: ReaderFontIconKind,
        private val iconColor: Int,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = iconColor
            paint.strokeWidth = (2f * resources.displayMetrics.density).coerceAtLeast(1f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.style = Paint.Style.STROKE
            val size = min(width, height) * 0.48f
            when (kind) {
                ReaderFontIconKind.DRAG -> CommonIconPainter.drawDrag(canvas, paint, width / 2f, height / 2f, size)
                ReaderFontIconKind.DELETE -> CommonIconPainter.drawDelete(canvas, paint, rect, width / 2f, height / 2f, size)
                ReaderFontIconKind.RADIO_SELECTED -> CommonIconPainter.drawRadioSelected(canvas, paint, width / 2f, height / 2f, size)
            }
        }
    }
}

package com.xiyunmn.cwmhook.feature.readerfont

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.cwmhook.config.readerfont.ReaderFontConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import java.io.File
import java.util.Locale

internal class ReaderFontSettingsInjector(
    private val typefaceProvider: ReaderFontTypefaceProvider,
    private val logTag: String,
) {
    companion object {
        const val REQUEST_IMPORT_FONT = 0x434D464E
    }

    private val panelTag = "cwmhook_reader_font_import_panel"
    private val customRowTagPrefix = "cwmhook_reader_font_row:"
    private val customCheckTagPrefix = "cwmhook_reader_font_check:"

    fun inject(activity: Activity, activityClass: Class<*>) {
        runCatching {
            if (!typefaceProvider.isEnabled(activity)) {
                removeInjectedViews(activity)
                return@runCatching
            }
            insertImportPanel(activity)
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
        val uri = data?.data ?: data?.clipData?.getItemAt(0)?.uri
        if (uri == null) {
            Toast.makeText(activity, "未读取到字体文件", Toast.LENGTH_SHORT).show()
            return true
        }

        runCatching {
            val font = copyFontToPrivateDir(activity, uri)
            if (!typefaceProvider.validate(font.absolutePath)) {
                font.delete()
                Toast.makeText(activity, "字体文件无法加载", Toast.LENGTH_SHORT).show()
                return true
            }
            selectCustomFont(activity, activityClass, font.absolutePath)
            ModuleFileLogger.i(logTag, "Imported reader font: ${font.absolutePath}")
        }.onFailure { throwable ->
            ModuleFileLogger.e(logTag, "Failed to import reader font", throwable)
            Toast.makeText(activity, "导入字体失败", Toast.LENGTH_SHORT).show()
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
                view.visibility = if (tag.removePrefix(customCheckTagPrefix) == currentPath) View.VISIBLE else View.GONE
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
        textView.text = typefaceProvider.displayName(path)
        typefaceProvider.load(path)?.let { textView.typeface = it }
    }

    private fun insertImportPanel(activity: Activity) {
        val root = contentLinearRoot(activity) ?: return
        if (root.findViewWithTag<View>(panelTag) != null) {
            return
        }

        val panel = LinearLayout(activity).apply {
            tag = panelTag
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10))
            setBackgroundColor(hostColor(activity, "color_bg_1", Color.WHITE))
        }
        panel.addView(TextView(activity).apply {
            text = "自定义字体"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(hostColor(activity, "textTitle", 0xFF202124.toInt()))
            includeFontPadding = false
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(TextView(activity).apply {
            text = "通过系统文件选择器导入 .ttf / .otf / .ttc，文件会复制到刺猬猫私有目录"
            textSize = 12f
            setTextColor(0xFF828282.toInt())
            setPadding(0, dp(activity, 5), 0, dp(activity, 9))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(TextView(activity).apply {
            text = "导入字体文件"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFFE95B89.toInt(), dp(activity, 8).toFloat())
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                startFontPicker(activity)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42)))

        val insertIndex = 2.coerceAtMost(root.childCount)
        root.addView(panel, insertIndex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun startFontPicker(activity: Activity) {
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_IMPORT_FONT)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "无法打开系统文件选择器", Toast.LENGTH_SHORT).show()
        }
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
        val row = RelativeLayout(activity).apply {
            tag = customRowTagPrefix + path
            setBackgroundColor(hostColor(activity, "color_bg_1", Color.WHITE))
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
            setTextColor(hostColor(activity, "textTitle", 0xFF202124.toInt()))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typefaceProvider.load(path)?.let { typeface = it }
        })
        textColumn.addView(TextView(activity).apply {
            text = path
            textSize = 12f
            includeFontPadding = false
            setTextColor(0xFF828282.toInt())
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
        val selectedDrawable = hostDrawableId(activity, "selected")
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

    private fun selectCustomFont(activity: Activity, activityClass: Class<*>, path: String) {
        ReaderFontConfigStore.rememberFont(activity, path)
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
        rebuildCustomRows(activity, activityClass)
        updateCustomChecks(activity)
        Toast.makeText(activity, "已选择 ${typefaceProvider.displayName(path)}", Toast.LENGTH_SHORT).show()
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

    private fun hostColor(activity: Activity, name: String, fallback: Int): Int {
        val id = activity.resources.getIdentifier(name, "color", activity.packageName)
        if (id == 0) {
            return fallback
        }
        return runCatching { activity.resources.getColor(id, activity.theme) }.getOrDefault(fallback)
    }

    private fun hostDrawableId(activity: Activity, name: String): Int {
        return activity.resources.getIdentifier(name, "drawable", activity.packageName)
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
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

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density + 0.5f).toInt()
    }
}

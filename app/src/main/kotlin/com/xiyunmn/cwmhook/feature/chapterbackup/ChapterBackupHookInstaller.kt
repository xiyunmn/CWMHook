package com.xiyunmn.cwmhook.feature.chapterbackup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.core.icons.CommonIconPainter
import com.xiyunmn.cwmhook.core.hostui.HostSkinPalette
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import kotlin.math.min

internal class ChapterBackupHookInstaller(
    private val logTag: String,
) {
    private var catalogHookInstalled = false
    private var activityResultHookInstalled = false
    private var skinHookInstalled = false
    private var activityLifecycleHookInstalled = false
    private var exporter: ChapterBackupExporter? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (exporter == null) {
            exporter = ChapterBackupExporter(classLoader, logTag)
        }
        hookCatalogFragment(module, classLoader)
        hookActivityResult(module)
        hookActivityLifecycle(module)
        hookSkinChange(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        if (!catalogHookInstalled || !activityResultHookInstalled || !activityLifecycleHookInstalled || !skinHookInstalled) {
            ModuleFileLogger.i(logTag, "Retry chapter backup hooks: $reason")
        }
        install(module, classLoader)
    }

    fun exportCachedBooks(activity: Activity, callback: ChapterBackupExporter.Callback) {
        exporter?.exportCachedBooks(activity, callback) ?: callback.onFailure("章节备份尚未初始化")
    }

    private fun hookCatalogFragment(module: XposedModule, classLoader: ClassLoader) {
        if (catalogHookInstalled) {
            return
        }
        val fragmentClass = XposedCompat.findClassOrNull(CiweiMaoClasses.CATALOG_FRAGMENT, classLoader) ?: run {
            ModuleFileLogger.i(logTag, "FragmentCatalog3 not visible yet, catalog export hook deferred")
            return
        }
        val method = runCatching {
            fragmentClass.getDeclaredMethod(
                "onCreateView",
                android.view.LayoutInflater::class.java,
                ViewGroup::class.java,
                android.os.Bundle::class.java,
            ).also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(logTag, "FragmentCatalog3.onCreateView not found", throwable)
            return
        }
        val hooked = XposedCompat.interceptProtective(module, method, "$logTag.FragmentCatalog3.onCreateView") { chain ->
            val result = chain.proceed()
            injectCatalogExportEntry(chain.thisObject)
            result
        }
        if (hooked) {
            catalogHookInstalled = true
            ModuleFileLogger.i(logTag, "Chapter backup catalog hook installed")
        }
    }

    private fun injectCatalogExportEntry(fragment: Any?) {
        val downLay = fragment?.declaredField("downLay") as? View ?: return
        if (!ChapterBackupConfigStore.readLocal(downLay.context).enabled) {
            return
        }
        val parent = downLay.parent as? ViewGroup ?: return
        if ((0 until parent.childCount).any { parent.getChildAt(it).tag == CATALOG_EXPORT_TAG }) {
            return
        }
        val activity = downLay.context.findActivity() ?: return
        val downIndex = parent.indexOfChild(downLay).takeIf { it >= 0 } ?: return
        val labelColor = ((fragment.declaredField("tv2") as? TextView)?.currentTextColor)
            ?: ChapterBackupSkinBridge.color(activity, "text_333333", Color.BLACK)
        val iconColor = HostSkinPalette.from(activity).accent
        val sourceDivider = fragment.declaredField("divView") as? View
        val restoredBookInfo = fragment.catalogBookInfo()
        val restoredDownloadType = fragment.catalogDownloadType()
        val exportLay = createCatalogExportEntry(activity, labelColor, iconColor) {
            val bookInfo = fragment.catalogBookInfo()
            val downloadType = fragment.catalogDownloadType()
            val currentExporter = exporter
            ModuleFileLogger.i(
                logTag,
                "Catalog export entry clicked: activity=${activity.javaClass.name}, bookInfo=${bookInfo?.javaClass?.name ?: "null"}",
            )
            if (currentExporter == null) {
                android.widget.Toast.makeText(activity, "章节导出尚未初始化", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                ChapterBackupFeature.showCatalogExportSelector(
                    activity,
                    currentExporter,
                    bookInfo,
                    downloadType,
                )
            }
        }
        val entryParams = cloneLayoutParams(downLay.layoutParams)
        parent.addView(exportLay, downIndex, entryParams)
        if (parent is LinearLayout) {
            val divider = createCatalogDivider(activity, sourceDivider)
            parent.addView(divider, downIndex + 1)
        }
        ModuleFileLogger.i(logTag, "Catalog export entry injected: ${activity.javaClass.name}")
        exporter?.let { currentExporter ->
            ChapterExportSelectionWindow.restoreIfNeeded(
                activity,
                currentExporter,
                restoredBookInfo,
                restoredDownloadType,
                "FragmentCatalog3.onCreateView",
            )
        }
    }

    private fun createCatalogExportEntry(
        activity: Activity,
        labelColor: Int,
        iconColor: Int,
        onClick: () -> Unit,
    ): RelativeLayout {
        return RelativeLayout(activity).apply {
            tag = CATALOG_EXPORT_TAG
            isClickable = true
            isFocusable = true
            setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        CatalogExportIconView(activity, iconColor),
                        LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24)),
                    )
                    addView(
                        TextView(activity).apply {
                            text = "章节导出"
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setTextColor(labelColor)
                            includeFontPadding = false
                            val textColorId = activity.resources.getIdentifier("text_333333", "color", activity.packageName)
                            ChapterBackupSkinBridge.applyAttr(this, "textColor", textColorId)
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(activity, 10)
                        },
                    )
                },
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    addRule(RelativeLayout.CENTER_IN_PARENT)
                },
            )
        }
    }

    private fun createCatalogDivider(activity: Activity, source: View?): View {
        val params = source?.layoutParams?.let { cloneLayoutParams(it) }
            ?: LinearLayout.LayoutParams(dp(activity, 1), dp(activity, 40)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        return CatalogDividerView(activity, source).apply {
            tag = CATALOG_EXPORT_DIVIDER_TAG
            layoutParams = params
        }
    }

    private fun cloneLayoutParams(source: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return when (source) {
            is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(source)
            is RelativeLayout.LayoutParams -> RelativeLayout.LayoutParams(source)
            else -> ViewGroup.LayoutParams(source)
        }
    }

    private fun Any.catalogBookInfo(): Any? {
        val catalogData = declaredField("catalogData") ?: return null
        return runCatching { catalogData.javaClass.getMethod("getBookInfo").invoke(catalogData) }.getOrNull()
    }

    private fun Any.catalogDownloadType(): String? {
        val catalogData = declaredField("catalogData")
        return if (catalogData?.booleanMethod("isIs_comic") == true) "comic" else null
    }

    private fun hookActivityResult(module: XposedModule) {
        if (activityResultHookInstalled) {
            return
        }
        val method = Activity::class.java.getDeclaredMethod(
            "onActivityResult",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Intent::class.java,
        ).also { it.isAccessible = true }
        val hooked = XposedCompat.hookAfter(module, method, "$logTag.Activity.onActivityResult") { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val requestCode = chain.getArg(0) as? Int ?: return@hookAfter
            val resultCode = chain.getArg(1) as? Int ?: return@hookAfter
            val data = chain.getArg(2) as? Intent
            ChapterBackupDirectoryPicker.handleActivityResult(activity, requestCode, resultCode, data, logTag)
        }
        if (hooked) {
            activityResultHookInstalled = true
            ModuleFileLogger.i(logTag, "Chapter backup Activity result hook installed")
        }
    }

    private fun hookActivityLifecycle(module: XposedModule) {
        if (activityLifecycleHookInstalled) {
            return
        }
        val createHooked = XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onCreate", android.os.Bundle::class.java),
            "$logTag.Activity.onCreate",
        ) { chain ->
            (chain.thisObject as? Activity)?.schedulePendingRestore("Activity.onCreate", 0L)
        }
        val postResumeHooked = XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onPostResume"),
            "$logTag.Activity.onPostResume",
        ) { chain ->
            (chain.thisObject as? Activity)?.schedulePendingRestore("Activity.onPostResume", 80L)
        }
        val focusHooked = XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType),
            "$logTag.Activity.onWindowFocusChanged",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val hasFocus = chain.getArg(0) as? Boolean ?: return@hookAfter
            if (hasFocus) {
                activity.schedulePendingRestore("Activity.onWindowFocusChanged", 40L)
            }
        }
        val saveStateHooked = XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onSaveInstanceState", android.os.Bundle::class.java),
            "$logTag.Activity.onSaveInstanceState",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            if (activity.isChapterBackupHostActivity()) {
                ChapterExportSelectionWindow.captureActiveForHostChange("Activity.onSaveInstanceState")
            }
        }
        val destroyHooked = XposedCompat.hookAfter(
            module,
            Activity::class.java.getDeclaredMethod("onDestroy"),
            "$logTag.Activity.onDestroy",
        ) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            if (activity.isChapterBackupHostActivity()) {
                ChapterExportSelectionWindow.handleHostActivityDestroy(activity, "Activity.onDestroy")
            }
        }
        if (createHooked && postResumeHooked && focusHooked && saveStateHooked && destroyHooked) {
            activityLifecycleHookInstalled = true
            ModuleFileLogger.i(logTag, "Chapter backup Activity lifecycle hooks installed")
        }
    }

    private fun Activity.schedulePendingRestore(reason: String, delayMs: Long) {
        if (!isChapterBackupHostActivity()) {
            return
        }
        if (!ChapterBackupConfigStore.readLocal(this).enabled) {
            return
        }
        val currentExporter = exporter ?: return
        window.decorView.postDelayed(
            {
                if (!isFinishing && !isDestroyedCompat()) {
                    val bookInfo = catalogActivityBookInfo() ?: return@postDelayed
                    ChapterExportSelectionWindow.restoreIfNeeded(
                        this,
                        currentExporter,
                        bookInfo,
                        catalogActivityDownloadType(),
                        reason,
                    )
                }
            },
            delayMs,
        )
    }

    private fun hookSkinChange(module: XposedModule, classLoader: ClassLoader) {
        if (skinHookInstalled) {
            return
        }
        val helperClass = XposedCompat.findClassOrNull(CiweiMaoClasses.SKIN_CHANGE_HELPER, classLoader) ?: return
        val listenerClass = XposedCompat.findClassOrNull(CiweiMaoClasses.SKIN_CHANGE_LISTENER, classLoader) ?: return
        var installed = false
        installed = hookSkinMethod(module, helperClass, "refreshSkin", listenerClass) || installed
        if (installed) {
            skinHookInstalled = true
            ModuleFileLogger.i(logTag, "Chapter backup skin hooks installed")
        }
    }

    private fun hookSkinMethod(
        module: XposedModule,
        helperClass: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Boolean {
        val method = runCatching {
            helperClass.getDeclaredMethod(methodName, *parameterTypes).also { it.isAccessible = true }
        }.getOrNull() ?: return false
        return XposedCompat.interceptProtective(module, method, "$logTag.SkinChangeHelper.$methodName") { chain ->
            ChapterExportSelectionWindow.captureActiveForHostChange("SkinChangeHelper.$methodName:before")
            val result = chain.proceed()
            ChapterExportSelectionWindow.scheduleHostSkinRefresh("SkinChangeHelper.$methodName")
            result
        }
    }

    private fun Activity.isChapterBackupHostActivity(): Boolean {
        return when (javaClass.name) {
            CiweiMaoClasses.CATALOG_ACTIVITY,
            CiweiMaoClasses.CATALOG_ACTIVITY_LANDSCAPE,
            CiweiMaoClasses.READER_ACTIVITY -> true
            else -> false
        }
    }

    private fun Activity.catalogActivityBookInfo(): Any? {
        declaredField("bookInfo")?.let { return it }
        return declaredField("catalogData")?.callNoArgMethod("getBookInfo")
    }

    private fun Activity.catalogActivityDownloadType(): String? {
        val type = declaredField("type") as? String
        if (type == "comic") {
            return "comic"
        }
        val catalogData = declaredField("catalogData")
        if (catalogData?.booleanMethod("isIs_comic") == true) {
            return "comic"
        }
        return if (catalogActivityBookInfo()?.stringMethod("getBook_type") == "2") {
            "comic"
        } else {
            null
        }
    }

    private fun Activity.isDestroyedCompat(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed
    }

    private class CatalogExportIconView(
        context: Context,
        private val fallbackColor: Int,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = HostSkinPalette.from(context).accent.takeIf { it != 0 } ?: fallbackColor
            paint.strokeWidth = (2f * resources.displayMetrics.density).coerceAtLeast(1f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.style = Paint.Style.STROKE
            CommonIconPainter.drawChapterExport(
                canvas = canvas,
                paint = paint,
                rect = rect,
                cx = width / 2f,
                cy = height / 2f,
                size = min(width, height) * 0.72f,
            )
        }
    }

    private class CatalogDividerView(
        context: Context,
        private val source: View?,
    ) : View(context) {
        private var lastSourceBackground: android.graphics.drawable.Drawable? = null

        override fun draw(canvas: Canvas) {
            syncWithSource()
            super.draw(canvas)
        }

        private fun syncWithSource() {
            val current = source?.background
            alpha = source?.alpha ?: 1f
            if (current == null) {
                if (background == null) {
                    background = android.graphics.drawable.ColorDrawable(HostSkinPalette.from(context).divider)
                }
                return
            }
            if (current === lastSourceBackground) {
                return
            }
            lastSourceBackground = current
            background = current.constantState?.newDrawable()?.mutate()
                ?: android.graphics.drawable.ColorDrawable(HostSkinPalette.from(context).divider)
        }
    }

    private companion object {
        const val CATALOG_EXPORT_TAG = "cwmhook_catalog_export_entry"
        const val CATALOG_EXPORT_DIVIDER_TAG = "cwmhook_catalog_export_divider"
    }
}

private fun dp(activity: Activity, value: Int): Int {
    return (value * activity.resources.displayMetrics.density + 0.5f).toInt()
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

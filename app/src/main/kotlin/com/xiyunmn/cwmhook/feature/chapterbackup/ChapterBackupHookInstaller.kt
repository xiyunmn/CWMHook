package com.xiyunmn.cwmhook.feature.chapterbackup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.config.chapterbackup.ChapterBackupConfigStore
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule

internal class ChapterBackupHookInstaller(
    private val logTag: String,
) {
    private var catalogHookInstalled = false
    private var activityResultHookInstalled = false
    private var skinHookInstalled = false
    private var exporter: ChapterBackupExporter? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (exporter == null) {
            exporter = ChapterBackupExporter(classLoader, logTag)
        }
        hookCatalogFragment(module, classLoader)
        hookActivityResult(module)
        hookSkinChange(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        if (!catalogHookInstalled || !activityResultHookInstalled || !skinHookInstalled) {
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
        val sourceDivider = fragment.declaredField("divView") as? View
        val restoredBookInfo = fragment.catalogBookInfo()
        val restoredDownloadType = fragment.catalogDownloadType()
        val exportLay = createCatalogExportEntry(activity, labelColor) {
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
            ChapterExportSelectionWindow.restoreIfNeeded(activity, currentExporter, restoredBookInfo, restoredDownloadType)
        }
    }

    private fun createCatalogExportEntry(
        activity: Activity,
        color: Int,
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
                        ImageView(activity).apply {
                            val icon = ChapterBackupSkinBridge.drawableId(activity, "contents_download")
                            if (icon != 0) {
                                setImageResource(icon)
                                ChapterBackupSkinBridge.applyAttr(this, "src", icon)
                            }
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                    addView(
                        TextView(activity).apply {
                            text = "章节导出"
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setTextColor(color)
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
        return LinearLayout.LayoutParams(dp(activity, 1), dp(activity, 40)).apply {
            gravity = Gravity.CENTER_VERTICAL
        }.let { params ->
            View(activity).apply {
                tag = CATALOG_EXPORT_DIVIDER_TAG
                background = source?.background?.constantState?.newDrawable()?.mutate()
                    ?: android.graphics.drawable.ColorDrawable(ChapterBackupSkinBridge.color(activity, "divider", Color.TRANSPARENT))
                val dividerId = activity.resources.getIdentifier("divider", "color", activity.packageName)
                ChapterBackupSkinBridge.applyAttr(this, "background", dividerId)
                layoutParams = params
            }
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

    private fun hookSkinChange(module: XposedModule, classLoader: ClassLoader) {
        if (skinHookInstalled) {
            return
        }
        val helperClass = XposedCompat.findClassOrNull(CiweiMaoClasses.SKIN_CHANGE_HELPER, classLoader) ?: return
        val listenerClass = XposedCompat.findClassOrNull(CiweiMaoClasses.SKIN_CHANGE_LISTENER, classLoader) ?: return
        var installed = false
        installed = hookSkinMethod(module, helperClass, "switchSkinMode", String::class.java, listenerClass) || installed
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
        return XposedCompat.hookAfter(module, method, "$logTag.SkinChangeHelper.$methodName") {
            ChapterExportSelectionWindow.scheduleHostSkinRefresh("SkinChangeHelper.$methodName")
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

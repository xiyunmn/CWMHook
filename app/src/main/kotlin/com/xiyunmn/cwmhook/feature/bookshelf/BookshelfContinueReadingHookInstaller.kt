package com.xiyunmn.cwmhook.feature.bookshelf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.xiyunmn.cwmhook.config.bookshelf.BookshelfConfigStore
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

internal class BookshelfContinueReadingHookInstaller(
    private val logTag: String,
) {
    private var installed = false

    fun install(module: XposedModule) {
        if (installed) {
            return
        }
        val methods = listOfNotNull(
            popupMethod("showAtLocation", View::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            popupMethod("showAsDropDown", View::class.java),
            popupMethod("showAsDropDown", View::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            popupMethod(
                "showAsDropDown",
                View::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ),
        )
        var hooked = false
        methods.forEach { method ->
            hooked = hookPopupShow(module, method) || hooked
        }
        if (hooked) {
            installed = true
            ModuleFileLogger.i(logTag, "Bookshelf continue reading popup hooks installed")
        }
    }

    private fun popupMethod(name: String, vararg parameterTypes: Class<*>?): Method? {
        val types = parameterTypes.filterNotNull().toTypedArray()
        return runCatching {
            PopupWindow::class.java.getDeclaredMethod(name, *types).also { it.isAccessible = true }
        }.getOrNull()
    }

    private fun hookPopupShow(module: XposedModule, method: Method): Boolean {
        return XposedCompat.interceptProtective(module, method, "$logTag.PopupWindow.${method.name}") { chain ->
            val popup = chain.thisObject as? PopupWindow
            val anchor = chain.args.firstOrNull() as? View
            if (popup != null && shouldHide(popup, anchor)) {
                ModuleFileLogger.throttled(
                    key = "$logTag.hideContinueReading",
                    intervalMs = 30_000L,
                    priority = android.util.Log.INFO,
                    tag = logTag,
                    message = "Bookshelf continue reading popup hidden",
                )
                null
            } else {
                chain.proceed()
            }
        }
    }

    private fun shouldHide(popup: PopupWindow, anchor: View?): Boolean {
        val content = popup.contentView ?: return false
        val activity = activityFromContext(anchor?.context)
            ?: activityFromContext(content.context)
            ?: return false
        if (activity.javaClass.name != CiweiMaoClasses.MAIN_FRAME_ACTIVITY) {
            return false
        }
        if (!BookshelfConfigStore.readLocal(activity).hideContinueReadingCard) {
            return false
        }
        val texts = collectTexts(content)
        return texts.any { it.contains("继续阅读") } &&
            texts.any { it.contains("上次读到") || it.contains("上次阅读") }
    }

    private fun activityFromContext(context: Context?): Activity? {
        var current = context
        repeat(8) {
            when (current) {
                is Activity -> return current
                is ContextWrapper -> current = current.baseContext
                else -> return null
            }
        }
        return null
    }

    private fun collectTexts(root: View): List<String> {
        val result = ArrayList<String>(8)
        fun visit(view: View, depth: Int) {
            if (depth > MAX_SCAN_DEPTH || result.size >= MAX_TEXTS) {
                return
            }
            if (view is TextView) {
                val text = view.text?.toString().orEmpty().trim()
                if (text.isNotBlank()) {
                    result += text
                }
            }
            if (view is ViewGroup) {
                val count = view.childCount.coerceAtMost(MAX_CHILDREN_PER_GROUP)
                for (index in 0 until count) {
                    visit(view.getChildAt(index), depth + 1)
                    if (result.size >= MAX_TEXTS) {
                        return
                    }
                }
            }
        }
        visit(root, 0)
        return result
    }

    private companion object {
        const val MAX_SCAN_DEPTH = 8
        const val MAX_TEXTS = 32
        const val MAX_CHILDREN_PER_GROUP = 24
    }
}

package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.PopupWindow
import android.widget.TextView
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.WeakHashMap

internal class StatusBarBookshelfBookPopupHookInstaller(
    private val setTransientOverlayVisible: (Activity, Boolean) -> Unit,
    private val logTag: String,
) {
    private val activePopups = WeakHashMap<PopupWindow, Activity>()
    private var frameworkHooksInstalled = false
    private var bookshelfHookInstalled = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        installFrameworkPopupHooks(module)
        installBookshelfHook(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        if (!bookshelfHookInstalled) {
            ModuleFileLogger.i(logTag, "Retry bookshelf book popup hook: $reason")
            installBookshelfHook(module, classLoader)
        }
    }

    private fun installFrameworkPopupHooks(module: XposedModule) {
        if (frameworkHooksInstalled) return
        var hooked = false
        popupShowMethods().forEach { method ->
            hooked = hookPopupShow(module, method) || hooked
        }
        popupMethod("dismiss")?.let { method ->
            hooked = XposedCompat.hookAfter(module, method, "$logTag.BookshelfBookPopup.dismiss") { chain ->
                val popup = chain.thisObject as? PopupWindow ?: return@hookAfter
                onPopupDismissed(popup)
            } || hooked
        }
        if (hooked) {
            frameworkHooksInstalled = true
            ModuleFileLogger.i(logTag, "Bookshelf book popup status bar hooks installed")
        }
    }

    private fun installBookshelfHook(module: XposedModule, classLoader: ClassLoader) {
        if (bookshelfHookInstalled) return
        val shelfFragmentClass = XposedCompat.findClassOrNull(CiweiMaoClasses.BOOK_SHELF_FRAGMENT, classLoader)
            ?: run {
                ModuleFileLogger.i(logTag, "BookShelfFrgment1 not visible yet, book popup hook deferred")
                return
            }
        val method = runCatching {
            shelfFragmentClass.getDeclaredMethod("showBookPop", Int::class.javaPrimitiveType).also {
                it.isAccessible = true
            }
        }.getOrNull() ?: run {
            ModuleFileLogger.w(logTag, "BookShelfFrgment1.showBookPop(int) not found")
            return
        }
        bookshelfHookInstalled = XposedCompat.interceptProtective(
            module,
            method,
            "$logTag.BookShelfFrgment1.showBookPop",
        ) { chain ->
            val fragment = chain.thisObject
            val activity = bookshelfActivityFromFragment(fragment)
            try {
                val result = chain.proceed()
                val popup = bookPopupFromFragment(fragment)
                if (activity != null && popup != null && popup.isShowing) {
                    onPopupShown(popup, activity)
                }
                result
            } catch (throwable: Throwable) {
                throw throwable
            }
        }
        if (bookshelfHookInstalled) {
            ModuleFileLogger.i(logTag, "BookShelfFrgment1.showBookPop hook installed")
        }
    }

    private fun hookPopupShow(module: XposedModule, method: Method): Boolean {
        return XposedCompat.hookAfter(module, method, "$logTag.BookshelfBookPopup.${method.name}") { chain ->
            val popup = chain.thisObject as? PopupWindow ?: return@hookAfter
            val anchor = chain.args.firstOrNull() as? View
            val activity = matchingBookshelfActivity(popup, anchor) ?: return@hookAfter
            onPopupShown(popup, activity)
        }
    }

    private fun onPopupShown(popup: PopupWindow, activity: Activity) {
        val alreadyTracked = synchronized(activePopups) {
            val tracked = activePopups[popup] === activity
            activePopups[popup] = activity
            tracked
        }
        if (alreadyTracked) {
            return
        }
        applyOverlayBeforePopupDraw(popup, activity)
    }

    private fun applyOverlayBeforePopupDraw(popup: PopupWindow, activity: Activity) {
        val content = popup.contentView
        if (content == null || !content.viewTreeObserver.isAlive) {
            applyOverlayIfPopupActive(popup, activity)
            return
        }
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    runCatching {
                        if (content.viewTreeObserver.isAlive) {
                            content.viewTreeObserver.removeOnPreDrawListener(this)
                        }
                    }
                    applyOverlayIfPopupActive(popup, activity)
                    return true
                }
            },
        )
    }

    private fun applyOverlayIfPopupActive(popup: PopupWindow, activity: Activity) {
        val stillActive = synchronized(activePopups) {
            activePopups[popup] === activity
        }
        if (!stillActive || !popup.isShowing || activity.isFinishing) {
            return
        }
        setTransientOverlayVisible(activity, true)
        ModuleFileLogger.throttled(
            key = "$logTag.bookshelfBookPopup.visible",
            intervalMs = 5_000L,
            priority = Log.DEBUG,
            tag = logTag,
            message = "Bookshelf book popup visible, status bar overlay enabled",
        )
    }

    private fun onPopupDismissed(popup: PopupWindow) {
        val activity = synchronized(activePopups) {
            activePopups.remove(popup)
        } ?: return
        val stillVisible = synchronized(activePopups) {
            activePopups.values.any { it === activity }
        }
        if (!stillVisible) {
            setTransientOverlayVisible(activity, false)
        }
    }

    private fun bookshelfActivityFromFragment(fragment: Any?): Activity? {
        if (fragment?.javaClass?.name != CiweiMaoClasses.BOOK_SHELF_FRAGMENT) {
            return null
        }
        return runCatching {
            fragment.javaClass.getDeclaredField("act").also { it.isAccessible = true }
                .get(fragment) as? Activity
        }.getOrNull()
    }

    private fun bookPopupFromFragment(fragment: Any?): PopupWindow? {
        if (fragment?.javaClass?.name != CiweiMaoClasses.BOOK_SHELF_FRAGMENT) {
            return null
        }
        return runCatching {
            fragment.javaClass.getDeclaredField("bookPop").also { it.isAccessible = true }
                .get(fragment) as? PopupWindow
        }.getOrNull()
    }

    private fun matchingBookshelfActivity(popup: PopupWindow, anchor: View?): Activity? {
        val content = popup.contentView ?: return null
        val activity = activityFromContext(anchor?.context)
            ?: activityFromContext(content.context)
            ?: return null
        if (activity.javaClass.name != CiweiMaoClasses.MAIN_FRAME_ACTIVITY) {
            return null
        }
        val texts = collectTexts(content)
        val isBookActionSheet = texts.any { it.contains("查看详情") } &&
            texts.any { it.contains("下载") } &&
            texts.any { it.contains("删除") } &&
            texts.any { it.contains("自动订阅") || it.contains("收费章节") }
        return if (isBookActionSheet) activity else null
    }

    private fun activityFromContext(context: Context?): Activity? {
        var current = context
        repeat(12) {
            when (current) {
                is Activity -> return current
                is ContextWrapper -> current = current.baseContext
                else -> return null
            }
        }
        return null
    }

    private fun collectTexts(root: View): List<String> {
        val result = ArrayList<String>(16)
        fun visit(view: View, depth: Int) {
            if (depth > MAX_SCAN_DEPTH || result.size >= MAX_TEXTS) return
            if (view is TextView) {
                val text = view.text?.toString().orEmpty().trim()
                if (text.isNotBlank()) result += text
            }
            if (view is ViewGroup) {
                val count = view.childCount.coerceAtMost(MAX_CHILDREN_PER_GROUP)
                for (index in 0 until count) {
                    visit(view.getChildAt(index), depth + 1)
                    if (result.size >= MAX_TEXTS) return
                }
            }
        }
        visit(root, 0)
        return result
    }

    private fun popupShowMethods(): List<Method> {
        return listOfNotNull(
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
    }

    private fun popupMethod(name: String, vararg parameterTypes: Class<*>?): Method? {
        val types = parameterTypes.filterNotNull().toTypedArray()
        return runCatching {
            PopupWindow::class.java.getDeclaredMethod(name, *types).also { it.isAccessible = true }
        }.getOrNull()
    }

    private companion object {
        const val MAX_SCAN_DEPTH = 8
        const val MAX_TEXTS = 40
        const val MAX_CHILDREN_PER_GROUP = 28
    }
}

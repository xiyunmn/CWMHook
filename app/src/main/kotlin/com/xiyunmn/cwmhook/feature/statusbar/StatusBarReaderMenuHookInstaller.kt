package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Window
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap

internal class StatusBarReaderMenuHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val updateReaderMenuSurface: (Window, Int?) -> Unit,
    private val logTag: String,
) {
    private val lastHostColors = WeakHashMap<Window, Int>()

    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        val titleBarClass = try {
            Class.forName(CiweiMaoClasses.READER_TITLE_BAR, false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(logTag, "ReaderTitleBar not visible yet, reader menu hook deferred")
            return false
        }
        val method = runCatching {
            titleBarClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType!!)
        }.getOrNull() ?: return false
        var installed = XposedCompat.interceptProtective(
            module,
            method,
            "$logTag.ReaderTitleBar.setVisibility",
        ) { chain ->
            val titleBar = chain.thisObject as? View
            val activity = titleBar?.let { windowRegistry.findActivity(it.context) }
            val visibility = chain.getArg(0) as? Int
            val result = chain.proceed()
            if (titleBar != null && activity != null && visibility == View.VISIBLE) {
                windowRegistry.rememberActivityWindow(activity)
                applyHostTitleColor(
                    activity,
                    titleBar,
                    reason = "ReaderTitleBar.setVisibility",
                )
            } else if (activity != null && visibility != View.VISIBLE) {
                updateReaderMenuSurface(activity.window, null)
            }
            result
        }
        if (installed) {
            ModuleFileLogger.i(logTag, "ReaderTitleBar hooks installed")
        }
        installed = installReaderShowTopSync(module, classLoader) || installed
        return installed
    }

    private fun installReaderShowTopSync(module: XposedModule, classLoader: ClassLoader): Boolean {
        val readerClass = runCatching {
            Class.forName(CiweiMaoClasses.READER_ACTIVITY, false, classLoader)
        }.getOrNull() ?: return false
        val showTop = runCatching {
            readerClass.getDeclaredMethod("showTop").also { it.isAccessible = true }
        }.getOrNull() ?: return false
        return XposedCompat.interceptProtective(
            module,
            showTop,
            "$logTag.ReaderActivity.showTop",
        ) { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? android.app.Activity ?: return@interceptProtective result
            val titleBar = reflectedView(activity, "titleBar") ?: return@interceptProtective result
            if (titleBar.visibility != View.VISIBLE) return@interceptProtective result
            applyHostTitleColor(activity, titleBar, "ReaderActivity.showTop")
            ModuleViewTaskRegistry.postOnAnimation(activity.window.decorView) {
                if (titleBar.visibility == View.VISIBLE) {
                    applyHostTitleColor(activity, titleBar, "ReaderActivity.showTop.nextFrame")
                }
            }
            result
        }
    }

    private fun reflectedView(activity: android.app.Activity, fieldName: String): View? {
        var type: Class<*>? = activity.javaClass
        while (type != null) {
            val field = runCatching { type.getDeclaredField(fieldName).also { it.isAccessible = true } }.getOrNull()
            if (field != null) return runCatching { field.get(activity) as? View }.getOrNull()
            type = type.superclass
        }
        return null
    }

    private fun applyHostTitleColor(
        activity: android.app.Activity,
        titleBar: View,
        reason: String,
    ) {
        val window = activity.window
        val color = resolveHostTitleColor(titleBar) ?: lastHostColors[window]
        if (color == null) {
            ModuleFileLogger.w(logTag, "Reader host title color unavailable: reason=$reason")
            return
        }
        lastHostColors[window] = color
        updateReaderMenuSurface(window, color)
        ModuleFileLogger.throttled(
            key = "reader-host-color:${System.identityHashCode(window)}:$reason",
            intervalMs = 500L,
            priority = android.util.Log.DEBUG,
            tag = logTag,
            message = "reader host title color=#%08X reason=$reason".format(color),
        )
    }

    private fun resolveHostTitleColor(titleBar: View): Int? {
        val titleId = titleBar.resources.getIdentifier("title", "id", titleBar.context.packageName)
        val title = if (titleId != 0) titleBar.findViewById<View>(titleId) else null
        return solidColor(title) ?: solidColor(titleBar)
    }

    private fun solidColor(view: View?): Int? {
        return when (val background = view?.background) {
            is ColorDrawable -> background.color
            is GradientDrawable -> background.color?.defaultColor
            else -> null
        }
    }

}

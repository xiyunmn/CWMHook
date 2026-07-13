package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule

internal class StatusBarBookDetailHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val applyWindow: (android.view.Window, String, Boolean, String?) -> Unit,
    private val captureBookDetailHero: (Activity, View) -> Unit,
    private val updateBookDetailScroll: (Activity, Int) -> Boolean,
    private val onBookDetailResume: (Activity) -> Unit,
    private val onBookDetailPause: (Activity) -> Unit,
    private val onBookDetailDestroy: (Activity) -> Unit,
    private val hookHelper: StatusBarHostHookHelper,
    private val logTag: String,
) {
    private var scrollHookInstalled = false
    private var backgroundHookInstalled = false
    private var lifecycleHooksInstalled = false

    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        if (!scrollHookInstalled) {
            scrollHookInstalled = installScrollHook(module, classLoader)
        }
        if (!backgroundHookInstalled) {
            backgroundHookInstalled = installBackgroundHook(module, classLoader)
        }
        if (!lifecycleHooksInstalled) {
            lifecycleHooksInstalled = installLifecycleHooks(module, classLoader)
        }
        return scrollHookInstalled && backgroundHookInstalled && lifecycleHooksInstalled
    }

    private fun installScrollHook(module: XposedModule, classLoader: ClassLoader): Boolean {
        val listenerClass = runCatching {
            Class.forName("${CiweiMaoClasses.BOOK_DETAIL_ACTIVITY}\$7", false, classLoader)
        }.getOrElse {
            ModuleFileLogger.i(logTag, "BookDetail scroll listener not visible yet, hook deferred")
            return false
        }
        val installed = hookHelper.hookMethodIfPresent(
            module,
            listenerClass,
            "onScroll",
            Int::class.javaPrimitiveType!!,
        ) { chain ->
            val listener = chain.thisObject ?: return@hookMethodIfPresent
            val activity = findOuterActivity(listener) ?: return@hookMethodIfPresent
            windowRegistry.rememberActivityWindow(activity)
            val state = windowRegistry.state(activity.window)
            state.bumpGeneration("BookDetail.onScroll")
            val scrollY = chain.getArg(0) as? Int ?: return@hookMethodIfPresent
            if (!updateBookDetailScroll(activity, scrollY)) {
                // The hero overlay must be replaced in the same traversal in
                // which the host makes titleLayout opaque. Posting this work
                // leaves one rendered frame with a transparent status bar.
                applyWindow(activity.window, "BookDetail.onScroll", true, null)
            }
        }
        if (installed) ModuleFileLogger.i(logTag, "BookDetail scroll hook installed")
        return installed
    }

    private fun installBackgroundHook(module: XposedModule, classLoader: ClassLoader): Boolean {
        val topClass = runCatching {
            Class.forName(CiweiMaoClasses.BOOK_INFO_TOP_LAYOUT, false, classLoader)
        }.getOrElse {
            ModuleFileLogger.i(logTag, "BookInfoTopLayout not visible yet, hero hook deferred")
            return false
        }
        val installed = hookHelper.hookMethodIfPresent(
            module,
            topClass,
            "blurBitmap",
            Bitmap::class.java,
            View::class.java,
        ) { chain ->
            val backgroundView = chain.getArg(1) as? View ?: return@hookMethodIfPresent
            val activity = windowRegistry.findActivity(backgroundView.context) ?: return@hookMethodIfPresent
            if (activity.javaClass.name != CiweiMaoClasses.BOOK_DETAIL_ACTIVITY) return@hookMethodIfPresent
            // This is an after-hook: blurBitmap has already assigned the final
            // BitmapDrawable. Apply the status-bar segment in the same UI turn
            // so the host background and its extension become visible together.
            captureBookDetailHero(activity, backgroundView)
        }
        if (installed) ModuleFileLogger.i(logTag, "Book detail hero background hook installed")
        return installed
    }

    private fun installLifecycleHooks(module: XposedModule, classLoader: ClassLoader): Boolean {
        val activityClass = runCatching {
            Class.forName(CiweiMaoClasses.BOOK_DETAIL_ACTIVITY, false, classLoader)
        }.getOrElse {
            ModuleFileLogger.i(logTag, "BookDetail activity not visible yet, lifecycle hooks deferred")
            return false
        }
        val resumeInstalled = hookHelper.hookMethodIfPresent(module, activityClass, "onResume") { chain ->
            (chain.thisObject as? Activity)?.let(onBookDetailResume)
        }
        val pauseInstalled = hookHelper.hookMethodIfPresent(module, activityClass, "onPause") { chain ->
            (chain.thisObject as? Activity)?.let(onBookDetailPause)
        }
        val destroyInstalled = hookHelper.hookMethodIfPresent(module, activityClass, "onDestroy") { chain ->
            (chain.thisObject as? Activity)?.let(onBookDetailDestroy)
        }
        val installed = resumeInstalled && pauseInstalled && destroyInstalled
        if (installed) ModuleFileLogger.i(logTag, "Book detail lifecycle hooks installed")
        return installed
    }

    private fun findOuterActivity(listener: Any): Activity? {
        return listener.javaClass.declaredFields.asSequence()
            .onEach { it.isAccessible = true }
            .mapNotNull { runCatching { it.get(listener) as? Activity }.getOrNull() }
            .firstOrNull { it.javaClass.name == CiweiMaoClasses.BOOK_DETAIL_ACTIVITY }
    }
}

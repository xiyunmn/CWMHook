package com.xiyunmn.cwmhook.feature.settings

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

internal class ModuleSettingsHookInstaller(
    private val logTag: String,
    private val onBookShelfEntryReady: (Any) -> Unit,
    private val onMainFrameActivityReady: (Activity, String) -> Unit,
    private val onReaderActivityReady: (Activity, String) -> Unit,
    private val onMainFrameActivitySaveState: (Activity, String) -> Unit,
    private val onMainFrameActivityDestroy: (Activity, String) -> Unit,
    private val onHostThemeChangeStarted: (String) -> Unit,
    private val onHostThemeChanged: (String) -> Unit,
    private val isBackKey: (KeyEvent?) -> Boolean,
    private val hasPanel: (Activity) -> Boolean,
    private val closePanel: (Activity, String) -> Boolean,
) {
    private var bookShelfHookInstalled = false
    private var activityHookInstalled = false
    private var skinHookInstalled = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookMainActivityLifecycle(module)
        hookSkinChange(module, classLoader)
        hookBookShelfEntry(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hookMainActivityLifecycle(module)
        hookSkinChange(module, classLoader)
        if (!bookShelfHookInstalled) {
            ModuleFileLogger.i(logTag, "Retry bookshelf module settings entry hook: $reason")
            hookBookShelfEntry(module, classLoader)
        }
    }

    private fun hookBookShelfEntry(module: XposedModule, classLoader: ClassLoader) {
        if (bookShelfHookInstalled) {
            return
        }
        val fragmentClass = try {
            Class.forName(CiweiMaoClasses.BOOK_SHELF_FRAGMENT, false, classLoader)
        } catch (_: Throwable) {
            ModuleFileLogger.i(logTag, "BookShelfFrgment1 not visible yet, module settings entry hook deferred")
            return
        }
        val method = runCatching {
            fragmentClass.getDeclaredMethod(
                "onCreateView",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Bundle::class.java,
            ).also { it.isAccessible = true }
        }.getOrElse { throwable ->
            ModuleFileLogger.e(logTag, "BookShelfFrgment1.onCreateView not found", throwable)
            return
        }

        val hooked = hookAfter(module, method) { chain ->
            onBookShelfEntryReady(chain.thisObject)
        }
        if (!hooked) {
            return
        }
        bookShelfHookInstalled = true
        ModuleFileLogger.i(logTag, "Bookshelf module settings entry hook installed")
    }

    private fun hookMainActivityLifecycle(module: XposedModule) {
        if (activityHookInstalled) {
            return
        }
        val resumeHooked = hookAfter(module, Activity::class.java.getDeclaredMethod("onPostResume")) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            activity.window.decorView.post {
                dispatchActivityReady(activity, "activity:onPostResume")
            }
        }
        val focusHooked = hookAfter(module, Activity::class.java.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType)) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val hasFocus = chain.args.firstOrNull() as? Boolean ?: return@hookAfter
            if (hasFocus) {
                activity.window.decorView.post {
                    dispatchActivityReady(activity, "activity:onWindowFocus")
                }
            }
        }
        val saveStateHooked = hookAfter(module, Activity::class.java.getDeclaredMethod("onSaveInstanceState", Bundle::class.java)) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            if (activity.isModuleSettingsHostActivity()) {
                onMainFrameActivitySaveState(activity, "activity:onSaveInstanceState")
            }
        }
        val destroyHooked = hookAfter(module, Activity::class.java.getDeclaredMethod("onDestroy")) { chain ->
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            if (activity.isModuleSettingsHostActivity()) {
                onMainFrameActivityDestroy(activity, "activity:onDestroy")
            }
        }
        val backHooked = hookBackClose(module)
        if (!resumeHooked || !focusHooked || !saveStateHooked || !destroyHooked || !backHooked) {
            return
        }
        activityHookInstalled = true
        ModuleFileLogger.i(logTag, "Module settings Activity lifecycle hooks installed")
    }

    private fun dispatchActivityReady(activity: Activity, reason: String) {
        when (activity.javaClass.name) {
            CiweiMaoClasses.MAIN_FRAME_ACTIVITY -> onMainFrameActivityReady(activity, reason)
            CiweiMaoClasses.READER_ACTIVITY -> onReaderActivityReady(activity, reason)
        }
    }

    private fun Activity.isModuleSettingsHostActivity(): Boolean {
        return javaClass.name == CiweiMaoClasses.MAIN_FRAME_ACTIVITY ||
            javaClass.name == CiweiMaoClasses.READER_ACTIVITY
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
            ModuleFileLogger.i(logTag, "Module settings skin hooks installed")
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
            onHostThemeChangeStarted("SkinChangeHelper.$methodName:before")
            val result = chain.proceed()
            onHostThemeChanged("SkinChangeHelper.$methodName")
            result
        }
    }

    private fun hookBackClose(module: XposedModule): Boolean {
        val dispatchHooked = XposedCompat.interceptProtective(
            module,
            Activity::class.java.getDeclaredMethod("dispatchKeyEvent", KeyEvent::class.java),
            "$logTag.Activity.dispatchKeyEvent",
        ) { chain ->
            val activity = chain.thisObject as? Activity
            val event = chain.args.firstOrNull() as? KeyEvent
            if (activity != null && isBackKey(event) && hasPanel(activity)) {
                if (event?.action == KeyEvent.ACTION_UP) {
                    closePanel(activity, "dispatchKeyEvent")
                }
                true
            } else {
                chain.proceed()
            }
        }

        val backPressedHooked = XposedCompat.interceptProtective(
            module,
            Activity::class.java.getDeclaredMethod("onBackPressed"),
            "$logTag.Activity.onBackPressed",
        ) { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && closePanel(activity, "onBackPressed")) {
                null
            } else {
                chain.proceed()
            }
        }
        return dispatchHooked && backPressedHooked
    }

    private fun hookAfter(
        module: XposedModule,
        executable: Executable,
        after: (XposedInterface.Chain) -> Unit,
    ): Boolean {
        val feature = "$logTag.${executable.declaringClass.name}.${executable.name}"
        return XposedCompat.hookAfter(module, executable, feature, after)
    }
}

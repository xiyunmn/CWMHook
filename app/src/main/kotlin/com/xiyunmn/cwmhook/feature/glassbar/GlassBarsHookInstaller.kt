package com.xiyunmn.cwmhook.feature.glassbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

internal class GlassBarsHookInstaller(
    private val onReady: (Activity) -> Unit,
    private val onFocus: (Activity, Boolean) -> Unit,
    private val onPause: (Activity) -> Unit,
    private val onDestroy: (Activity) -> Unit,
    private val onTouch: (Activity, MotionEvent, (MotionEvent) -> Boolean) -> Boolean,
) {
    private val installed = mutableSetOf<String>()

    fun install(module: XposedModule, classLoader: ClassLoader) {
        after(module, "onPostResume") { activity, _ -> onReady(activity) }
        after(module, "onContentChanged") { activity, _ -> onReady(activity) }
        after(module, "onConfigurationChanged", Configuration::class.java) { activity, _ -> onReady(activity) }
        after(module, "onWindowFocusChanged", Boolean::class.javaPrimitiveType!!) { activity, chain ->
            onFocus(activity, chain.getArg(0) as Boolean)
        }
        after(module, "onPause") { activity, _ -> onPause(activity) }
        after(module, "onDestroy") { activity, _ -> onDestroy(activity) }
        installTouch(module)
        installCatalog(module, classLoader)
    }

    private fun installTouch(module: XposedModule) {
        val name = "dispatchTouchEvent"
        if (name in installed) return
        val method = Activity::class.java.getDeclaredMethod(name, MotionEvent::class.java)
        if (XposedCompat.interceptProtective(module, method, "$TAG.Activity.$name") { chain ->
                val activity = chain.thisObject as? Activity ?: return@interceptProtective chain.proceed()
                val event = chain.getArg(0) as? MotionEvent ?: return@interceptProtective chain.proceed()
                onTouch(activity, event) { forwarded -> chain.proceed(arrayOf(forwarded)) as Boolean }
            }
        ) installed += name
    }

    private fun after(
        module: XposedModule,
        name: String,
        vararg parameters: Class<*>,
        callback: (Activity, XposedInterface.Chain) -> Unit,
    ) {
        if (name in installed) return
        val method = Activity::class.java.getDeclaredMethod(name, *parameters)
        if (XposedCompat.hookAfter(module, method, "$TAG.Activity.$name") { chain ->
                (chain.thisObject as? Activity)?.let { callback(it, chain) }
            }
        ) installed += name
    }

    private fun installCatalog(module: XposedModule, classLoader: ClassLoader) {
        val key = "catalog.onCreateView"
        if (key in installed) return
        val type = XposedCompat.findClassOrNull(CiweiMaoClasses.CATALOG_FRAGMENT, classLoader) ?: return
        val method = runCatching {
            type.getDeclaredMethod("onCreateView", LayoutInflater::class.java, ViewGroup::class.java, Bundle::class.java)
        }.getOrNull() ?: return
        if (XposedCompat.interceptProtective(module, method, "$TAG.FragmentCatalog3.onCreateView") { chain ->
                val result = chain.proceed()
                val root = result as? View
                val activity = root?.context?.activity()
                if (root != null && activity != null) {
                    // Wait for the fragment to attach and for the existing export-entry hook to finish.
                    ModuleViewTaskRegistry.post(root) { onReady(activity) }
                }
                result
            }
        ) installed += key
    }

    private fun Context.activity(): Activity? {
        var context: Context? = this
        repeat(12) {
            if (context is Activity) return context as Activity
            val next = (context as? ContextWrapper)?.baseContext ?: return null
            if (next === context) return null
            context = next
        }
        return null
    }

    private companion object { const val TAG = "CWMHook.GlassBars" }
}

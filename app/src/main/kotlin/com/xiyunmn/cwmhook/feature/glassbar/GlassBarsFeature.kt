package com.xiyunmn.cwmhook.feature.glassbar

import android.app.Activity
import com.xiyunmn.cwmhook.config.glassbar.GlassBarsConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.HostProcessInspector
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule
import java.util.IdentityHashMap

object GlassBarsFeature {
    private const val TAG = "CWMHook.GlassBars"
    private val controllers = IdentityHashMap<Activity, GlassActivityController>()
    private val hooks = GlassBarsHookInstaller(
        onReady = ::refresh,
        onFocus = { activity, active ->
            if (active) refresh(activity)
            controllers[activity]?.setActive(active)
        },
        onPause = { controllers[it]?.setActive(false) },
        onDestroy = { controllers.remove(it)?.close() },
        onTouch = { activity, event, dispatchNative ->
            controllers[activity]?.dispatchTouch(event, dispatchNative) ?: dispatchNative(event)
        },
    )

    fun install(module: XposedModule, classLoader: ClassLoader) = hooks.install(module, classLoader)

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hooks.install(module, classLoader)
    }

    /** Called after settings have been committed; existing and paused pages use the same config. */
    fun applyRuntimeConfig() {
        (controllers.keys.toList() + HostProcessInspector.activities()).distinct().forEach(::refresh)
    }

    fun prepareForHotReload() {
        controllers.values.toList().forEach { controller ->
            runCatching { controller.close() }.onFailure { ModuleFileLogger.w(TAG, "Glass cleanup failed", it) }
        }
        controllers.clear()
    }

    private fun refresh(activity: Activity) {
        val kind = kindFor(activity.javaClass.name) ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            controllers.remove(activity)?.close()
            return
        }
        val config = GlassBarsConfigStore.readLocal(activity)
        if (!kind.enabled(config)) {
            controllers.remove(activity)?.close()
            return
        }
        val controller = controllers.getOrPut(activity) { GlassActivityController(activity, kind) }
        controller.apply(config)
        controller.setActive(activity.hasWindowFocus())
    }

    private fun kindFor(className: String): GlassBarKind? = when (className) {
        CiweiMaoClasses.MAIN_FRAME_ACTIVITY -> GlassBarKind.MAIN
        CiweiMaoClasses.BOOK_DETAIL_ACTIVITY -> GlassBarKind.DETAIL
        CiweiMaoClasses.CATALOG_ACTIVITY, CiweiMaoClasses.CATALOG_ACTIVITY_LANDSCAPE,
        CiweiMaoClasses.READER_ACTIVITY, "${CiweiMaoClasses.READER_ACTIVITY}_LANDSCAPE" -> GlassBarKind.CATALOG
        else -> null
    }
}

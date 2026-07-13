package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.xiyunmn.cwmhook.config.statusbar.StatusBarConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap

internal class StatusBarFeatureGraph(
    private val config: StatusBarFeatureConfig,
) {
    private val cacheKeys = StatusBarCacheKeys(config.cacheVersion, config.mainFrameActivity)
    private val sceneRules = StatusBarSceneRules(cacheKeys, config.readerActivity)
    private val colorResolver = StatusBarColorResolver(config.targetPackage, config.targetDefaultPref, sceneRules)
    private val paddingController = StatusBarPaddingController()
    private val scrimController = StatusBarScrimController(config.scrimTag)
    private val windowController = StatusBarWindowController()
    private val viewTreeTools = StatusBarViewTreeTools(config.maxTraversedViews)
    private val windowRegistry = StatusBarWindowRegistry(cacheKeys, config.maxSceneCacheSize)
    private val transientOverlayRegistry = StatusBarTransientOverlayRegistry(windowRegistry)
    private val applyPolicy = StatusBarApplyPolicy(
        sceneRules = sceneRules,
        enabledProvider = { context -> StatusBarConfigStore.readLocal(context).enabled },
        readerMenuVisible = { view -> isReaderMenuVisible(view) },
        transientOverlayVisible = { window -> transientOverlayRegistry.isVisible(window) },
        registeredMainWindow = windowRegistry::isRegisteredMainWindow,
    )
    private val colorSampler = StatusBarColorSampler(
        viewTreeTools = viewTreeTools,
        scrimController = scrimController,
        maxBackgroundScanViews = config.maxBackgroundScanViews,
        sampleBitmapWidth = config.sampleBitmapWidth,
        sampleBitmapHeight = config.sampleBitmapHeight,
        logTag = config.logTag,
    )
    private val cacheLock = Any()
    private val applying = ThreadLocal.withInitial { false }
    private var persistentCache: StatusBarColorCache? = null
    private val transientOverlayBaseColors = WeakHashMap<Window, Int>()

    private val sceneResolver = StatusBarSceneResolver(
        sceneRules,
        viewTreeTools,
        windowRegistry,
        config.pagePagerIds,
    )
    private val colorCoordinator = StatusBarColorCoordinator(
        sceneRules = sceneRules,
        colorResolver = colorResolver,
        colorSampler = colorSampler,
        scrimController = scrimController,
        windowController = windowController,
        windowRegistry = windowRegistry,
        colorCacheProvider = { context -> colorCache(context) },
        readerMenuVisible = { view -> isReaderMenuVisible(view) },
        canSampleWindow = { window -> applyPolicy.canSample(window) },
        sampleDelayMs = config.sampleDelayMs,
        sampleMinIntervalMs = config.sampleMinIntervalMs,
        logTag = config.logTag,
    )
    private val runtimeApplier = StatusBarRuntimeApplier(
        sceneRules = sceneRules,
        colorResolver = colorResolver,
        colorCoordinator = colorCoordinator,
        paddingController = paddingController,
        scrimController = scrimController,
        windowController = windowController,
        windowRegistry = windowRegistry,
        sceneResolver = sceneResolver,
        colorCacheProvider = { context -> colorCache(context) },
        applyPolicy = applyPolicy,
        readerMenuVisible = { view -> isReaderMenuVisible(view) },
        applying = applying,
        logTag = config.logTag,
    )
    private val scheduler = StatusBarFeatureScheduler(
        mainFrameActivity = config.mainFrameActivity,
        windowRegistry = windowRegistry,
        sceneResolver = sceneResolver,
        runtimeApplier = runtimeApplier,
    )
    private val hookInstaller = StatusBarHookInstaller(
        windowRegistry = windowRegistry,
        isApplying = { applying.get() },
        applyWindow = { window, reason, forceSample, sceneKeyOverride ->
            runtimeApplier.apply(window, reason, forceSample, sceneKeyOverride)
        },
        updateReaderMenuSurface = runtimeApplier::updateReaderMenuSurface,
        captureBookDetailHero = runtimeApplier::captureBookDetailHero,
        updateBookDetailScroll = runtimeApplier::updateBookDetailScroll,
        onBookDetailResume = runtimeApplier::onBookDetailResume,
        onBookDetailPause = runtimeApplier::onBookDetailPause,
        onBookDetailDestroy = runtimeApplier::onBookDetailDestroy,
        applyIfNeeded = runtimeApplier::applyIfNeeded,
        shouldManageWindow = runtimeApplier::shouldManageWindow,
        ensureTransparentStatusBarColor = runtimeApplier::ensureTransparentStatusBarColor,
        scheduleApplyForPageView = scheduler::scheduleApplyForPageView,
        scheduleKnownWindows = scheduler::scheduleKnownWindows,
        applyFragmentWindow = scheduler::applyFragmentWindow,
        isReaderActivity = sceneRules::isReaderActivity,
        setTransientOverlayVisible = ::setTransientOverlayVisible,
        logTag = config.logTag,
    )

    fun install(module: XposedModule, classLoader: ClassLoader) {
        hookInstaller.install(module, classLoader)
        module.log(Log.INFO, config.logTag, "Immersive status bar feature installed")
        ModuleFileLogger.i(config.logTag, "Immersive status bar feature installed")
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        hookInstaller.retryDeferredHooks(module, classLoader, reason)
    }

    private fun colorCache(context: Context): StatusBarColorCache {
        persistentCache?.let { return it }
        return synchronized(cacheLock) {
            persistentCache ?: StatusBarColorCache(
                context = context.applicationContext ?: context,
                keys = cacheKeys,
                prefName = config.cachePref,
                keyListName = config.cacheKeysName,
                maxSceneCacheSize = config.maxSceneCacheSize,
            ).also { persistentCache = it }
        }
    }

    private fun isReaderMenuVisible(root: View): Boolean {
        val titleBar = viewTreeTools.findViewByResourceName(root, "titleBar") ?: return false
        return titleBar.visibility == View.VISIBLE
    }

    fun clearColorCache(context: Context): Boolean {
        return runCatching {
            val cache = colorCache(context)
            cache.clear()
            ModuleFileLogger.i(config.logTag, "Status bar color cache cleared")
            true
        }.getOrElse { throwable ->
            ModuleFileLogger.e(config.logTag, "Failed to clear status bar cache", throwable)
            false
        }
    }

    fun reapplyForegroundWindow(reason: String) {
        runCatching {
            val foregroundWindow = windowRegistry.foregroundWindows().firstOrNull()
            if (foregroundWindow != null) {
                runtimeApplier.apply(foregroundWindow, reason, forceSample = false, sceneKeyOverride = null)
                ModuleFileLogger.i(config.logTag, "Reapplied foreground window: reason=$reason")
            } else {
                ModuleFileLogger.w(config.logTag, "No foreground window to reapply")
            }
        }.onFailure { throwable ->
            ModuleFileLogger.e(config.logTag, "Failed to reapply foreground window", throwable)
        }
    }

    fun setTransientOverlayVisible(activity: Activity, visible: Boolean) {
        transientOverlayRegistry.setVisible(activity, visible)
        windowRegistry.state(activity.window).bumpGeneration("transientOverlay:$visible")
        val contentRoot = activity.window.findViewById<ViewGroup>(android.R.id.content)
        val overlayColor = contentRoot?.let { scrimController.setOverlay(it, visible) }
        val statusBarOverlayColor = overlayColor ?: statusBarOverlayColor(activity.window, visible)
        if (statusBarOverlayColor != null) {
            windowController.applySystemBarAppearance(activity.window, activity.window.decorView, statusBarOverlayColor)
        }
        if (!visible) {
            ModuleViewTaskRegistry.post(activity.window.decorView) {
                runtimeApplier.apply(activity.window, "transientOverlayClosed", forceSample = true)
            }
        }
        ModuleFileLogger.throttled(
            key = "transient-overlay:${activity.javaClass.name}:$visible",
            intervalMs = 500L,
            priority = Log.DEBUG,
            tag = config.logTag,
            message = "transient overlay visible=$visible activity=${activity.javaClass.name}",
        )
    }

    private fun statusBarOverlayColor(window: Window, visible: Boolean, dimFraction: Float = 0.50f): Int? {
        return synchronized(transientOverlayBaseColors) {
            if (visible) {
                val base = transientOverlayBaseColors.getOrPut(window) { window.statusBarColor }
                darken(base, dimFraction)
            } else {
                transientOverlayBaseColors.remove(window)
            }
        }
    }

    private fun darken(color: Int, fraction: Float): Int {
        if (fraction <= 0f) return color
        val factor = 1f - fraction.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt(),
        )
    }

    fun prepareForHotReload(activities: List<Activity>) {
        val windows = activities.map { it.window }
        runtimeApplier.prepareForHotReload(windows)
        windowController.prepareForHotReload(windows)
        paddingController.restoreAllForHotReload()
        scrimController.clearForHotReload()
        transientOverlayRegistry.clearForHotReload()
        synchronized(transientOverlayBaseColors) {
            transientOverlayBaseColors.clear()
        }
        windowRegistry.clearForHotReload()
    }
}

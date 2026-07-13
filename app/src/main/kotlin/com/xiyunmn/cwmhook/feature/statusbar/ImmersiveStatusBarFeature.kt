package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import com.xiyunmn.cwmhook.host.CiweiMaoIds
import com.xiyunmn.cwmhook.host.CiweiMaoPackages
import io.github.libxposed.api.XposedModule

object ImmersiveStatusBarFeature {
    private const val TAG = "CWMHook.StatusBar"
    private const val CACHE_PREF = "cwmhook_status_bar_cache"
    private const val CACHE_KEYS = "keys"
    private const val CACHE_VERSION = "explicit-v1"
    private const val SCRIM_TAG = "cwmhook_status_bar_scrim"
    private const val SAMPLE_BITMAP_WIDTH = 32
    private const val SAMPLE_BITMAP_HEIGHT = 3
    private const val SAMPLE_DELAY_MS = 48L
    private const val SAMPLE_MIN_INTERVAL_MS = 240L
    private const val MAX_SCENE_CACHE_SIZE = 64
    private const val MAX_TRAVERSED_VIEWS = 320
    private const val MAX_BACKGROUND_SCAN_VIEWS = 180

    private val graph = StatusBarFeatureGraph(
        StatusBarFeatureConfig(
            logTag = TAG,
            targetPackage = CiweiMaoPackages.NOVEL,
            targetDefaultPref = CiweiMaoPackages.DEFAULT_PREF,
            cachePref = CACHE_PREF,
            cacheKeysName = CACHE_KEYS,
            cacheVersion = CACHE_VERSION,
            scrimTag = SCRIM_TAG,
            mainFrameActivity = CiweiMaoClasses.MAIN_FRAME_ACTIVITY,
            readerActivity = CiweiMaoClasses.READER_ACTIVITY,
            pagePagerIds = setOf(
                CiweiMaoIds.MAIN_VIEW_PAGER,
                CiweiMaoIds.VIEW_PAGER_ALT,
                CiweiMaoIds.VIEW_PAGER_LOWER,
                CiweiMaoIds.PAGER,
                CiweiMaoIds.PAGER_1,
            ),
            sampleBitmapWidth = SAMPLE_BITMAP_WIDTH,
            sampleBitmapHeight = SAMPLE_BITMAP_HEIGHT,
            sampleDelayMs = SAMPLE_DELAY_MS,
            sampleMinIntervalMs = SAMPLE_MIN_INTERVAL_MS,
            maxSceneCacheSize = MAX_SCENE_CACHE_SIZE,
            maxTraversedViews = MAX_TRAVERSED_VIEWS,
            maxBackgroundScanViews = MAX_BACKGROUND_SCAN_VIEWS,
        ),
    )

    fun install(module: XposedModule, classLoader: ClassLoader) {
        graph.install(module, classLoader)
    }

    fun retryDeferredHooks(module: XposedModule, classLoader: ClassLoader, reason: String) {
        graph.retryDeferredHooks(module, classLoader, reason)
    }

    /**
     * 清除状态栏取色缓存
     *
     * 用于用户遇到颜色明显不对时手动清理
     */
    fun clearColorCache(context: android.content.Context): Boolean {
        return graph.clearColorCache(context)
    }

    /**
     * 重新应用当前前台窗口的状态栏
     *
     * 用于清缓存或修改配置后立即观察效果
     */
    fun reapplyForegroundWindow(reason: String) {
        graph.reapplyForegroundWindow(reason)
    }

    fun setTransientOverlayVisible(activity: Activity, visible: Boolean) {
        graph.setTransientOverlayVisible(activity, visible)
    }

    fun prepareForHotReload(activities: List<Activity>) {
        graph.prepareForHotReload(activities)
    }
}

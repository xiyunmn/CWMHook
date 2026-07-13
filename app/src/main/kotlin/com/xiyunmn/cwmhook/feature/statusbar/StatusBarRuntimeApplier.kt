package com.xiyunmn.cwmhook.feature.statusbar

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.Window
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import java.util.Locale
import java.util.WeakHashMap

internal class StatusBarRuntimeApplier(
    private val sceneRules: StatusBarSceneRules,
    private val colorResolver: StatusBarColorResolver,
    private val colorCoordinator: StatusBarColorCoordinator,
    private val paddingController: StatusBarPaddingController,
    private val scrimController: StatusBarScrimController,
    private val windowController: StatusBarWindowController,
    private val windowRegistry: StatusBarWindowRegistry,
    private val sceneResolver: StatusBarSceneResolver,
    private val colorCacheProvider: (Context) -> StatusBarColorCache,
    private val applyPolicy: StatusBarApplyPolicy,
    private val readerMenuVisible: (View) -> Boolean,
    private val applying: ThreadLocal<Boolean>,
    private val logTag: String,
) {
    private val integrityChecker = StatusBarWindowIntegrityChecker(
        sceneRules = sceneRules,
        scrimController = scrimController,
        windowController = windowController,
        readerMenuVisible = readerMenuVisible,
    )
    private val contentInsetApplier = StatusBarContentInsetApplier(
        colorCoordinator = colorCoordinator,
        paddingController = paddingController,
        scrimController = scrimController,
        windowController = windowController,
    )
    private val bookDetailHeroController = StatusBarBookDetailHeroController(windowController)
    private val bookDetailCaptureGenerations = WeakHashMap<Window, Int>()

    fun apply(
        window: Window,
        reason: String,
        forceSample: Boolean = false,
        sceneKeyOverride: String? = null,
    ) {
        if (applying.get()) {
            return
        }
        applying.set(true)
        try {
            val decorView = window.decorView
            if (isApplyBlocked(window, reason)) {
                if (applyPolicy.applyBlockReason(window) == "disabled") {
                    bookDetailHeroController.clear(window)
                }
                return
            }
            if (bookDetailHeroController.reapplyHandoffIfActive(window)) {
                return
            }
            val state = windowRegistry.state(window)
            val sceneKey = sceneKeyOverride ?: sceneResolver.resolveWindowSceneKey(window, decorView, state)
            val readerScene = sceneRules.isReaderScene(sceneKey)
            if (readerScene) {
                return
            }
            if (sceneRules.isBookDetailScene(sceneKey)) {
                val activity = windowRegistry.findActivityForWindow(window, decorView)
                if (activity != null && !bookDetailHeroController.reapplyIfActive(window)) {
                    captureCurrentBookDetailHero(activity)
                    scheduleBookDetailHeroCapture(activity)
                }
                if (bookDetailHeroController.reapplyIfActive(window)) {
                    return
                }
            }
            val skinKey = colorResolver.currentSkinKey(decorView.context)
            val persistedColor: Int? = null
            val restored = state.activate(sceneKey, skinKey, persistedColor)
            if (forceSample) {
                state.markDirty(clearCached = true)
            }

            windowController.configureTransparentStatusBar(window, decorView)

            val topInset = if (windowController.isFullscreen(window, decorView)) 0 else windowController.statusBarHeight(decorView)
            val surfaceColor = if (topInset > 0) {
                colorResolver.directSceneColor(decorView.context, skinKey, sceneKey)
                    ?: state.cachedColor
                    ?: window.statusBarColor.takeIf { Color.alpha(it) == 255 }
                    ?: colorResolver.fallbackColor(decorView.context, skinKey)
            } else {
                null
            }

            windowController.applySystemBarAppearance(window, decorView, surfaceColor)
            contentInsetApplier.apply(window, decorView, topInset, surfaceColor, state, forceSample)

            ModuleFileLogger.throttled(
                key = "apply:${System.identityHashCode(window)}:$reason",
                intervalMs = 800L,
                priority = Log.DEBUG,
                tag = logTag,
                message = "apply reason=$reason skin=$skinKey scene=$sceneKey topInset=$topInset " +
                    "color=${formatColor(surfaceColor)} restored=$restored dirty=${state.colorDirty}",
            )
        } catch (throwable: Throwable) {
            Log.e(logTag, "Failed to apply immersive status bar: $reason", throwable)
            ModuleFileLogger.e(logTag, "Failed to apply immersive status bar: $reason", throwable)
        } finally {
            applying.set(false)
        }
    }

    fun applyIfNeeded(window: Window, reason: String) {
        if (applying.get()) {
            return
        }
        val decorView = window.decorView
        if (isApplyBlocked(window, reason)) {
            return
        }
        val state = windowRegistry.state(window)
        val sceneKey = sceneResolver.resolveWindowSceneKey(window, decorView, state)
        val skinKey = colorResolver.currentSkinKey(decorView.context)
        if (
            state.isCleanFor(sceneKey, skinKey) &&
            integrityChecker.isIntact(window, decorView, state)
        ) {
            return
        }
        apply(window, reason, sceneKeyOverride = sceneKey)
    }

    fun shouldManageWindow(window: Window): Boolean {
        return runCatching {
            val decorView = window.decorView
            if (!applyPolicy.shouldManageWindow(window)) {
                return@runCatching false
            }
            val state = windowRegistry.state(window)
            val sceneKey = sceneResolver.resolveWindowSceneKey(window, decorView, state)
            !applyPolicy.isReaderBypass(sceneKey, decorView)
        }.getOrDefault(false)
    }

    fun ensureTransparentStatusBarColor(window: Window) {
        if (window.statusBarColor == Color.TRANSPARENT) {
            return
        }
        applying.set(true)
        try {
            window.statusBarColor = Color.TRANSPARENT
        } finally {
            applying.set(false)
        }
    }

    private fun formatColor(color: Int?): String {
        return color?.let { "#%08X".format(Locale.US, it) } ?: "null"
    }

    fun updateReaderMenuSurface(window: Window, hostColor: Int?) {
        if (hostColor == null) {
            windowController.clearReaderStatusBarFrame(window, window.decorView)
            return
        }
        val blockReason = applyPolicy.applyBlockReason(window)
        if (blockReason != null) {
            ModuleFileLogger.throttled(
                key = "reader-direct-filter:${System.identityHashCode(window)}:$blockReason",
                intervalMs = 800L,
                priority = Log.DEBUG,
                tag = logTag,
                message = "reader direct apply filtered rule=$blockReason",
            )
            return
        }
        val decorView = window.decorView
        windowController.applyReaderStatusBarFrame(
            window,
            decorView,
            hostColor,
            updateIconAppearance = true,
        )
    }

    fun captureBookDetailHero(activity: android.app.Activity, heroBackgroundView: View) {
        val window = activity.window
        val blockReason = applyPolicy.applyBlockReason(window)
        if (blockReason != null) {
            if (blockReason == "disabled") {
                bookDetailHeroController.clear(window)
            }
            return
        }
        windowRegistry.rememberActivityWindow(activity)
        val scrollY = bookDetailScrollView(activity)?.scrollY ?: 0
        bookDetailHeroController.capture(window, heroBackgroundView, scrollY)
    }

    fun updateBookDetailScroll(activity: android.app.Activity, scrollY: Int): Boolean {
        val window = activity.window
        val blockReason = applyPolicy.applyBlockReason(window)
        if (blockReason != null) {
            if (blockReason == "disabled") {
                bookDetailHeroController.clear(window)
            }
            return false
        }
        windowRegistry.rememberActivityWindow(activity)
        return bookDetailHeroController.updateScroll(window, scrollY)
    }

    fun onBookDetailResume(activity: android.app.Activity) {
        Log.i(BOOK_DETAIL_PROBE_TAG, "resume activity=${probeId(activity)} window=${probeId(activity.window)}")
        windowRegistry.rememberActivityWindow(activity)
        val scrollView = bookDetailScrollView(activity)
        scrollView?.overScrollMode = View.OVER_SCROLL_NEVER
        bookDetailHeroController.updateScroll(activity.window, scrollView?.scrollY ?: 0)
        captureCurrentBookDetailHero(activity)
        scheduleBookDetailHeroCapture(activity)
    }

    fun onBookDetailPause(activity: android.app.Activity) {
        Log.i(BOOK_DETAIL_PROBE_TAG, "pause activity=${probeId(activity)} window=${probeId(activity.window)}")
    }

    fun onBookDetailDestroy(activity: android.app.Activity) {
        Log.i(BOOK_DETAIL_PROBE_TAG, "destroy activity=${probeId(activity)} window=${probeId(activity.window)}")
        synchronized(bookDetailCaptureGenerations) {
            bookDetailCaptureGenerations.remove(activity.window)
        }
        bookDetailHeroController.release(activity.window)
    }

    private fun captureCurrentBookDetailHero(activity: android.app.Activity): Boolean {
        if (activity.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return false
        val heroViewId = activity.resources.getIdentifier(
            "mainlay",
            "id",
            activity.packageName,
        )
        if (heroViewId == 0) return false
        val heroView = activity.findViewById<View>(heroViewId) ?: return false
        val scrollY = bookDetailScrollView(activity)?.scrollY ?: 0
        val captured = bookDetailHeroController.capture(activity.window, heroView, scrollY)
        if (captured) {
            synchronized(bookDetailCaptureGenerations) {
                bookDetailCaptureGenerations.remove(activity.window)
            }
        }
        return captured
    }

    private fun scheduleBookDetailHeroCapture(activity: android.app.Activity) {
        val window = activity.window
        val generation = synchronized(bookDetailCaptureGenerations) {
            val next = (bookDetailCaptureGenerations[window] ?: 0) + 1
            bookDetailCaptureGenerations[window] = next
            next
        }
        BOOK_DETAIL_CAPTURE_RETRY_MS.forEach { delayMs ->
            window.decorView.postDelayed({
                val current = synchronized(bookDetailCaptureGenerations) {
                    bookDetailCaptureGenerations[window]
                }
                if (current != generation) return@postDelayed
                if (captureCurrentBookDetailHero(activity)) {
                    bookDetailHeroController.reapplyIfActive(window)
                }
            }, delayMs)
        }
    }

    private fun bookDetailScrollView(activity: android.app.Activity): View? {
        val scrollViewId = activity.resources.getIdentifier(
            "scroolview",
            "id",
            activity.packageName,
        )
        if (scrollViewId == 0) return null
        return activity.findViewById(scrollViewId)
    }

    private fun isApplyBlocked(window: Window, reason: String): Boolean {
        val blockReason = applyPolicy.applyBlockReason(window) ?: return false
        if (blockReason != "disabled") {
            ModuleFileLogger.throttled(
                key = "apply-filter:${System.identityHashCode(window)}:$blockReason",
                intervalMs = 800L,
                priority = Log.DEBUG,
                tag = logTag,
                message = "apply filtered reason=$reason rule=$blockReason",
            )
        }
        return true
    }

    private companion object {
        const val BOOK_DETAIL_PROBE_TAG = "CWMHook.BookDetailProbe"
        val BOOK_DETAIL_CAPTURE_RETRY_MS = longArrayOf(80L, 240L, 600L)
    }

    private fun probeId(value: Any): String = Integer.toHexString(System.identityHashCode(value))
}

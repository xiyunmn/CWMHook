package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.Window
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal class StatusBarBookDetailHeroController(
    private val windowController: StatusBarWindowController,
) {
    private data class HeroState(
        var sourceBitmapIdentity: Int = 0,
        var targetWidth: Int = 0,
        var totalHeight: Int = 0,
        var heroView: WeakReference<View>? = null,
        var originalBackground: Drawable? = null,
        var heroDrawable: ExpandedHeroDrawable? = null,
        var overlayDrawable: ExpandedHeroDrawable? = null,
        var overlayAttached: Boolean = false,
        var collapsed: Boolean = false,
        var scrollY: Int = 0,
    )

    private val states = WeakHashMap<Window, HeroState>()
    private data class HandoffState(val generation: Int, val drawable: Drawable, val lightIcons: Boolean)
    private val handoffs = WeakHashMap<Window, HandoffState>()
    private var handoffGeneration = 0

    fun capture(window: Window, heroBackgroundView: View, scrollY: Int): Boolean {
        probe("capture.enter w=${id(window)} viewBg=${heroBackgroundView.background?.javaClass?.name} scroll=$scrollY")
        val source = (heroBackgroundView.background as? BitmapDrawable)?.bitmap ?: return false
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return false
        val decorView = window.decorView
        val width = decorView.width.takeIf { it > 0 } ?: heroBackgroundView.width
        val inset = windowController.statusBarHeight(decorView)
        val heroHeight = heroBackgroundView.height
        if (width <= 0 || inset <= 0 || heroHeight <= 0) return false

        val state = states.getOrPut(window) { HeroState() }
        val threshold = (BOOK_DETAIL_COLLAPSE_DP * decorView.resources.displayMetrics.density).roundToInt()
        state.scrollY = scrollY.coerceAtLeast(0)
        state.collapsed = state.scrollY >= threshold
        val identity = System.identityHashCode(source)
        val totalHeight = heroHeight + inset
        if (state.sourceBitmapIdentity != identity || state.targetWidth != width || state.totalHeight != totalHeight) {
            detach(window, state)
            state.sourceBitmapIdentity = identity
            state.targetWidth = width
            state.totalHeight = totalHeight
            state.heroView = WeakReference(heroBackgroundView)
            state.originalBackground = heroBackgroundView.background
            state.heroDrawable = ExpandedHeroDrawable(
                sourceBitmap = source,
                totalHeight = totalHeight,
                segmentOffset = inset,
            )
            state.overlayDrawable = ExpandedHeroDrawable(
                sourceBitmap = source,
                totalHeight = totalHeight,
                segmentOffset = state.scrollY,
                tintColor = HERO_DARK_OVERLAY,
            ).apply {
                setBounds(0, 0, width, inset)
            }
            heroBackgroundView.background = state.heroDrawable
        }
        if (!state.collapsed) {
            show(window, state)
        }
        probe("capture.exit w=${id(window)} src=$identity collapsed=${state.collapsed} attached=${state.overlayAttached}")
        return !state.collapsed
    }

    fun updateScroll(window: Window, scrollY: Int): Boolean {
        val state = states.getOrPut(window) { HeroState() }
        val wasCollapsed = state.collapsed
        val threshold = (BOOK_DETAIL_COLLAPSE_DP * window.decorView.resources.displayMetrics.density).roundToInt()
        state.scrollY = scrollY.coerceAtLeast(0)
        state.collapsed = scrollY >= threshold
        if (wasCollapsed != state.collapsed || scrollY == 0) {
            probe("scroll w=${id(window)} y=$scrollY collapsed=${state.collapsed} drawable=${state.overlayDrawable != null}")
        }
        if (state.collapsed) {
            detach(window, state)
            return false
        }
        state.overlayDrawable?.segmentOffset = state.scrollY
        return show(window, state)
    }

    fun reapplyIfActive(window: Window): Boolean {
        val state = states[window] ?: return false
        if (state.collapsed) return false
        return show(window, state)
    }

    fun clear(window: Window) {
        val state = states.remove(window) ?: return
        probe("clear w=${id(window)} collapsed=${state.collapsed} attached=${state.overlayAttached}")
        detach(window, state)
        val heroView = state.heroView?.get()
        if (heroView != null && heroView.background === state.heroDrawable) {
            heroView.background = state.originalBackground
        }
    }

    fun release(window: Window) {
        val state = states.remove(window) ?: return
        clearHandoff(window)
        probe("release w=${id(window)} collapsed=${state.collapsed} attached=${state.overlayAttached}")
        // Do not mutate the dying Window's drawable tree here. Android can keep
        // rendering it for the activity exit transition after onDestroy; removing
        // the overlay or restoring the host background would expose an early
        // status-bar color change in the last visible frames.
    }

    fun clearForHotReload(windows: List<Window>) {
        val trackedWindows = (windows + states.keys + handoffs.keys).distinct()
        trackedWindows.forEach { window ->
            runCatching {
                clear(window)
                clearHandoff(window)
            }
        }
        states.clear()
        handoffs.clear()
    }

    fun beginHandoff(sourceWindow: Window, targetWindow: Window): Int? {
        val sourceState = states[sourceWindow] ?: return null
        clearHandoff(targetWindow)
        val targetDecor = targetWindow.decorView
        val width = targetDecor.width.takeIf { it > 0 } ?: sourceState.targetWidth
        val inset = windowController.statusBarHeight(targetDecor)
        if (width <= 0 || inset <= 0) return null
        val drawable: Drawable
        val lightIcons: Boolean
        val source = sourceState.heroDrawable?.sourceBitmap
        if (!sourceState.collapsed && source != null && !source.isRecycled) {
            drawable = ExpandedHeroDrawable(
                sourceBitmap = source,
                totalHeight = sourceState.totalHeight,
                segmentOffset = sourceState.scrollY,
                tintColor = HERO_DARK_OVERLAY,
            )
            lightIcons = false
        } else {
            val color = sourceWindow.statusBarColor.takeIf { Color.alpha(it) == 255 } ?: return null
            drawable = ColorDrawable(color)
            lightIcons = Color.luminance(color) > 0.55
        }
        drawable.setBounds(0, 0, width, inset)
        val generation = ++handoffGeneration
        handoffs[targetWindow] = HandoffState(generation, drawable, lightIcons)
        windowController.configureTransparentStatusBar(targetWindow, targetDecor)
        targetWindow.statusBarColor = Color.TRANSPARENT
        windowController.applyStatusBarIconAppearance(targetWindow, lightIcons)
        targetDecor.overlay.add(drawable)
        probe("handoff.begin source=${id(sourceWindow)} target=${id(targetWindow)} gen=$generation collapsed=${sourceState.collapsed}")
        return generation
    }

    fun reapplyHandoffIfActive(window: Window): Boolean {
        val handoff = handoffs[window] ?: return false
        windowController.configureTransparentStatusBar(window, window.decorView)
        window.statusBarColor = Color.TRANSPARENT
        windowController.applyStatusBarIconAppearance(window, handoff.lightIcons)
        return true
    }

    fun finishHandoff(window: Window, generation: Int): Boolean {
        val handoff = handoffs[window] ?: return false
        if (handoff.generation != generation) return false
        handoffs.remove(window)
        window.decorView.overlay.remove(handoff.drawable)
        probe("handoff.finish target=${id(window)} gen=$generation")
        return true
    }

    private fun clearHandoff(window: Window) {
        handoffs.remove(window)?.let { window.decorView.overlay.remove(it.drawable) }
    }

    private fun show(window: Window, state: HeroState): Boolean {
        val drawable = state.overlayDrawable ?: return false
        val decorView = window.decorView
        windowController.configureTransparentStatusBar(window, decorView)
        window.statusBarColor = Color.TRANSPARENT
        windowController.applyStatusBarIconAppearance(window, lightStatusBar = false)
        if (!state.overlayAttached) {
            decorView.overlay.add(drawable)
            state.overlayAttached = true
        }
        return true
    }

    private fun detach(window: Window, state: HeroState) {
        if (!state.overlayAttached) return
        state.overlayDrawable?.let(window.decorView.overlay::remove)
        state.overlayAttached = false
    }

    private class ExpandedHeroDrawable(
        val sourceBitmap: Bitmap,
        private val totalHeight: Int,
        segmentOffset: Int,
        private val tintColor: Int? = null,
    ) : Drawable() {
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var segmentOffset: Int = segmentOffset
            set(value) {
                if (field == value) return
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            if (sourceBitmap.isRecycled || bounds.isEmpty) return
            val saveCount = canvas.save()
            canvas.clipRect(bounds)
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat() - segmentOffset)
            canvas.scale(
                bounds.width().toFloat() / sourceBitmap.width,
                totalHeight.toFloat() / sourceBitmap.height,
            )
            canvas.drawBitmap(sourceBitmap, 0f, 0f, bitmapPaint)
            canvas.restoreToCount(saveCount)
            tintColor?.let {
                val tintSaveCount = canvas.save()
                canvas.clipRect(bounds)
                canvas.drawColor(it)
                canvas.restoreToCount(tintSaveCount)
            }
        }

        override fun setAlpha(alpha: Int) {
            bitmapPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            bitmapPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val PROBE_TAG = "CWMHook.BookDetailProbe"
        const val BOOK_DETAIL_COLLAPSE_DP = 48f
        const val HERO_DARK_OVERLAY = 0x99000000.toInt()
    }

    private fun id(value: Any): String = Integer.toHexString(System.identityHashCode(value))

    private fun probe(message: String) {
        Log.i(PROBE_TAG, message)
    }
}

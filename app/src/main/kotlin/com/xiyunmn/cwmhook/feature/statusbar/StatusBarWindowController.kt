package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.WindowInsetsController
import android.view.WindowManager
import java.util.WeakHashMap

internal class StatusBarWindowController {
    private val readerStatusBarOverlays = WeakHashMap<Window, ColorDrawable>()
    private val readerOverlayGenerations = WeakHashMap<Window, Int>()

    fun configureTransparentStatusBar(window: Window, decorView: View) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }
        decorView.systemUiVisibility = decorView.systemUiVisibility and
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN.inv()
    }

    fun applySystemBarAppearance(window: Window, decorView: View, surfaceColor: Int?) {
        if (surfaceColor != null && window.statusBarColor != surfaceColor) {
            window.statusBarColor = surfaceColor
        }
        val lightStatusBar = surfaceColor == null || Color.luminance(surfaceColor) > 0.55
        val currentFlags = decorView.systemUiVisibility
        val nextFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && lightStatusBar) {
            currentFlags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            currentFlags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        if (nextFlags != currentFlags) {
            decorView.systemUiVisibility = nextFlags
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appearance = if (lightStatusBar) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0
            window.insetsController?.setSystemBarsAppearance(
                appearance,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        }
    }

    fun applyReaderStatusBarFrame(
        window: Window,
        decorView: View,
        surfaceColor: Int,
        updateIconAppearance: Boolean,
    ) {
        readerOverlayGenerations[window] = (readerOverlayGenerations[window] ?: 0) + 1
        // Keep the host's fullscreen/system-bar flags untouched. ReaderActivity
        // shows status and navigation bars together; changing those flags while
        // both bars are hidden makes the navigation bar draw a default frame.
        if (window.statusBarColor != surfaceColor) {
            window.statusBarColor = surfaceColor
        }
        val overlay = readerStatusBarOverlays.getOrPut(window) {
            ColorDrawable(surfaceColor).also(decorView.overlay::add)
        }
        overlay.color = surfaceColor
        overlay.setBounds(0, 0, decorView.width, readerStatusBarOverlayHeight(decorView))
        if (updateIconAppearance && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appearance = if (Color.luminance(surfaceColor) > 0.55) {
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            } else {
                0
            }
            window.insetsController?.setSystemBarsAppearance(
                appearance,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        }
    }

    fun clearReaderStatusBarFrame(window: Window, decorView: View) {
        if (!readerStatusBarOverlays.containsKey(window)) return
        val generation = (readerOverlayGenerations[window] ?: 0) + 1
        readerOverlayGenerations[window] = generation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            decorView.postDelayed({ removeReaderStatusBarOverlay(window, decorView, generation) }, 350L)
            return
        }
        val fullHeight = readerStatusBarOverlayHeight(decorView)
        val exitColor = sampleReaderBackgroundColor(decorView, fullHeight)
        readerStatusBarOverlays[window]?.let { overlay ->
            overlay.color = exitColor
            overlay.setBounds(0, 0, decorView.width, fullHeight)
        }
        val callback = object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onProgress(
                insets: WindowInsets,
                runningAnimations: MutableList<WindowInsetsAnimation>,
            ): WindowInsets {
                if (readerOverlayGenerations[window] != generation) return insets
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimation) {
                if (animation.typeMask and WindowInsets.Type.statusBars() != 0) {
                    removeReaderStatusBarOverlay(window, decorView, generation)
                    decorView.setWindowInsetsAnimationCallback(null)
                }
            }
        }
        decorView.setWindowInsetsAnimationCallback(callback)
        decorView.postDelayed({
            decorView.setWindowInsetsAnimationCallback(null)
            removeReaderStatusBarOverlay(window, decorView, generation)
        }, READER_OVERLAY_CLEAR_FALLBACK_MS)
    }

    private fun removeReaderStatusBarOverlay(window: Window, decorView: View, generation: Int) {
        if (readerOverlayGenerations[window] != generation) return
        readerStatusBarOverlays.remove(window)?.let(decorView.overlay::remove)
    }

    private fun sampleReaderBackgroundColor(decorView: View, statusBarHeight: Int): Int {
        if (decorView.width <= 0 || decorView.height <= 0) return Color.BLACK
        val sampleX = 8.coerceAtMost(decorView.width - 1)
        val sampleY = (statusBarHeight + 16).coerceAtMost(decorView.height - 1)
        return runCatching {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.translate(-sampleX.toFloat(), -sampleY.toFloat())
                decorView.draw(canvas)
                bitmap.getPixel(0, 0)
            } finally {
                bitmap.recycle()
            }
        }.getOrDefault(Color.BLACK)
    }

    private fun readerStatusBarOverlayHeight(decorView: View): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val inset = decorView.rootWindowInsets
                ?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                ?.top
                ?: 0
            if (inset > 0) return inset
        }
        return statusBarHeight(decorView)
    }

    fun isFullscreen(window: Window, decorView: View): Boolean {
        return window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0 ||
            decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
    }

    fun statusBarHeight(view: View): Int {
        val resourceId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) view.resources.getDimensionPixelSize(resourceId) else 0
    }

    private companion object {
        const val READER_OVERLAY_CLEAR_FALLBACK_MS = 500L
    }
}

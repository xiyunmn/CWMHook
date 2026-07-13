package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.util.WeakHashMap

internal class StatusBarScrimController(
    private val scrimTag: String,
) {
    private val baseColors = WeakHashMap<View, Int>()
    private val overlayFractions = WeakHashMap<View, Float>()

    fun ensure(contentRoot: ViewGroup, height: Int, color: Int) {
        val scrim = find(contentRoot) ?: run {
            if (contentRoot !is FrameLayout) {
                return
            }
            View(contentRoot.context).also { view ->
                view.tag = scrimTag
                view.isClickable = false
                view.isFocusable = false
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                contentRoot.addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.TOP),
                )
            }
        }
        val layoutParams = scrim.layoutParams
        if (layoutParams.height != height) {
            layoutParams.height = height
            scrim.layoutParams = layoutParams
        }
        baseColors[scrim] = color
        val displayColor = darken(color, overlayFractions[scrim] ?: 0f)
        if (solidBackgroundColor(scrim) != displayColor) {
            scrim.setBackgroundColor(displayColor)
        }
        if (scrim.visibility != View.VISIBLE) {
            scrim.visibility = View.VISIBLE
        }
        scrim.bringToFront()
    }

    fun hide(contentRoot: ViewGroup) {
        find(contentRoot)?.visibility = View.GONE
    }

    fun setOverlay(contentRoot: ViewGroup, visible: Boolean, dimFraction: Float = 0.50f): Int? {
        val scrim = find(contentRoot) ?: return null
        if (visible) {
            overlayFractions[scrim] = dimFraction.coerceIn(0f, 1f)
        } else {
            overlayFractions.remove(scrim)
        }
        val base = baseColors[scrim] ?: solidBackgroundColor(scrim) ?: return null
        val display = darken(base, overlayFractions[scrim] ?: 0f)
        scrim.setBackgroundColor(display)
        return display
    }

    fun find(contentRoot: ViewGroup): View? {
        for (index in 0 until contentRoot.childCount) {
            val child = contentRoot.getChildAt(index)
            if (isScrim(child)) {
                return child
            }
        }
        return null
    }

    fun findAppRoot(contentRoot: ViewGroup): View? {
        for (index in 0 until contentRoot.childCount) {
            val child = contentRoot.getChildAt(index)
            if (!isScrim(child)) {
                return child
            }
        }
        return null
    }

    fun neutralizeStatusBarBackground(view: View?) {
        if (view?.id != android.R.id.statusBarBackground) {
            return
        }
        view.background = ColorDrawable(Color.TRANSPARENT)
        view.alpha = 0f
        view.visibility = View.INVISIBLE
    }

    fun isScrim(view: View?): Boolean {
        return view?.tag == scrimTag
    }

    fun solidBackgroundColor(view: View?): Int? {
        val color = when (val background = view?.background) {
            is ColorDrawable -> background.color
            is GradientDrawable -> background.color?.defaultColor ?: return null
            else -> return null
        }
        return if (Color.alpha(color) >= 230) color else null
    }

    fun clearForHotReload() {
        val scrims = (baseColors.keys + overlayFractions.keys).distinct()
        scrims.forEach { scrim ->
            runCatching { (scrim.parent as? ViewGroup)?.removeView(scrim) }
        }
        baseColors.clear()
        overlayFractions.clear()
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
}

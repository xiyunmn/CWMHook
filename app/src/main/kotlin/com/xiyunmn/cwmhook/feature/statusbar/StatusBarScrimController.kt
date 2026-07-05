package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

internal class StatusBarScrimController(
    private val scrimTag: String,
) {
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
        if (solidBackgroundColor(scrim) != color) {
            scrim.setBackgroundColor(color)
        }
        if (scrim.visibility != View.VISIBLE) {
            scrim.visibility = View.VISIBLE
        }
        scrim.bringToFront()
    }

    fun hide(contentRoot: ViewGroup) {
        find(contentRoot)?.visibility = View.GONE
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
}

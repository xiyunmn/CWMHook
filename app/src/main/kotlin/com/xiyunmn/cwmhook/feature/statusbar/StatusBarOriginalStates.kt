package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager

internal data class ReaderOriginalWindowState(
    val statusBarColor: Int,
    val systemUiVisibility: Int,
    val flags: Int,
    val cutoutMode: Int?,
    val contrastEnforced: Boolean?,
    val statusBarBackgroundState: ViewOriginalState?,
) {
    fun restore(window: Window, decorView: View) {
        restoreFlag(window, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        restoreFlag(window, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cutoutMode != null) {
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode = cutoutMode
            window.attributes = attributes
        }
        if (window.statusBarColor != statusBarColor) {
            window.statusBarColor = statusBarColor
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contrastEnforced != null) {
            window.isStatusBarContrastEnforced = contrastEnforced
        }
        if (decorView.systemUiVisibility != systemUiVisibility) {
            decorView.systemUiVisibility = systemUiVisibility
        }
        statusBarBackgroundState?.restore(decorView.findViewById(android.R.id.statusBarBackground))
    }

    private fun restoreFlag(window: Window, flag: Int) {
        if (flags and flag != 0) {
            window.addFlags(flag)
        } else {
            window.clearFlags(flag)
        }
    }

    companion object {
        fun from(window: Window, decorView: View): ReaderOriginalWindowState {
            return ReaderOriginalWindowState(
                statusBarColor = window.statusBarColor,
                systemUiVisibility = decorView.systemUiVisibility,
                flags = window.attributes.flags,
                cutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode
                } else {
                    null
                },
                contrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced
                } else {
                    null
                },
                statusBarBackgroundState = ViewOriginalState.from(
                    decorView.findViewById(android.R.id.statusBarBackground),
                ),
            )
        }
    }
}

internal data class ViewOriginalState(
    val background: Drawable?,
    val alpha: Float,
    val visibility: Int,
) {
    fun restore(view: View?) {
        if (view == null) {
            return
        }
        view.background = background
        view.alpha = alpha
        view.visibility = visibility
    }

    companion object {
        fun from(view: View?): ViewOriginalState? {
            return view?.let {
                ViewOriginalState(
                    background = it.background,
                    alpha = it.alpha,
                    visibility = it.visibility,
                )
            }
        }
    }
}

internal data class PaddingState(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    var appliedTop: Int,
) {
    fun wasChangedOutside(view: View): Boolean {
        return view.paddingLeft != left ||
            view.paddingRight != right ||
            view.paddingBottom != bottom ||
            view.paddingTop != appliedTop
    }

    companion object {
        fun from(view: View): PaddingState {
            return PaddingState(
                left = view.paddingLeft,
                top = view.paddingTop,
                right = view.paddingRight,
                bottom = view.paddingBottom,
                appliedTop = view.paddingTop,
            )
        }
    }
}

package com.xiyunmn.cwmhook.feature.statusbar

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager

internal class StatusBarWindowController {
    fun configureTransparentStatusBar(window: Window, decorView: View) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attributes
        }
        if (window.statusBarColor != Color.TRANSPARENT) {
            window.statusBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }
        decorView.systemUiVisibility = decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    fun applySystemBarAppearance(window: Window, decorView: View, surfaceColor: Int?) {
        val lightStatusBar = surfaceColor == null || Color.luminance(surfaceColor) > 0.55
        val currentFlags = decorView.systemUiVisibility
        val nextFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && lightStatusBar) {
            currentFlags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        } else {
            (currentFlags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()) or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
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

    fun isFullscreen(window: Window, decorView: View): Boolean {
        return window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0 ||
            decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
    }

    fun statusBarHeight(view: View): Int {
        val resourceId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) view.resources.getDimensionPixelSize(resourceId) else 0
    }
}

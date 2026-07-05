package com.xiyunmn.cwmhook.ui.panel

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xiyunmn.cwmhook.ui.common.dp
import kotlin.math.min

internal object FloatingPanelWindowMetrics {
    fun initialPanelParams(activity: Activity): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(panelWidth(activity), panelHeight(activity)).apply {
            gravity = Gravity.CENTER
        }
    }

    fun frameMatch(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun panelWidth(activity: Activity): Int {
        val config = activity.resources.configuration
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val available = screenWidth - dp(activity, 40)
        val ratio = if (isLandscape) 0.56f else 0.86f
        val byRatio = (screenWidth * ratio).toInt()
        val maxDp = if (isLandscape) 480 else 360
        return min(min(dp(activity, maxDp), byRatio), available).coerceAtLeast(min(dp(activity, 280), available))
    }

    private fun panelHeight(activity: Activity): Int {
        val screenHeight = activity.resources.displayMetrics.heightPixels
        val available = screenHeight -
            statusBarHeight(activity) -
            navigationBarHeight(activity) -
            dp(activity, 48)
        val byRatio = (screenHeight * 0.68f).toInt()
        val minHeight = min(dp(activity, 360), (available * 0.5f).toInt())
        return min(min(dp(activity, 600), byRatio), available).coerceAtLeast(minHeight)
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun navigationBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id != 0) context.resources.getDimensionPixelSize(id) else 0
    }
}

package com.xiyunmn.cwmhook.ui.panel

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xiyunmn.cwmhook.ui.common.PanelTheme

object FloatingPanelWindow {
    private const val OVERLAY_TAG = "com.xiyunmn.cwmhook.FLOATING_PANEL"

    fun show(
        activity: Activity,
        createPanel: (Activity, FrameLayout, PanelTheme) -> View,
        onShown: (Activity) -> Unit,
        onReused: (Activity) -> Unit,
        onClosed: (String) -> Unit,
    ) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val existing = content.findViewWithTag<View>(OVERLAY_TAG)
        if (existing != null) {
            existing.visibility = View.VISIBLE
            existing.alpha = 0f
            existing.bringToFront()
            existing.requestFocus()
            existing.animate()
                .alpha(1f)
                .setDuration(200L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            onReused(activity)
            return
        }

        val theme = PanelTheme.from(activity)
        val overlay = FrameLayout(activity).apply {
            tag = OVERLAY_TAG
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            clipChildren = false
            clipToPadding = false
            alpha = 0f
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    close(this, "viewKey", onClosed)
                    true
                } else {
                    false
                }
            }
        }
        val dim = View(activity).apply {
            setBackgroundColor(theme.dim)
            isClickable = true
            setOnClickListener { close(overlay, "dim", onClosed) }
        }
        overlay.addView(dim, FloatingPanelWindowMetrics.frameMatch())

        val panel = createPanel(activity, overlay, theme)
        panel.scaleX = 0.85f
        panel.scaleY = 0.85f
        panel.alpha = 0f
        overlay.addView(panel, FloatingPanelWindowMetrics.initialPanelParams(activity))
        content.addView(overlay, FloatingPanelWindowMetrics.frameMatch())
        overlay.requestFocus()

        overlay.animate()
            .alpha(1f)
            .setDuration(250L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        panel.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(300L)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
            .start()
        onShown(activity)
    }

    fun hasPanel(activity: Activity): Boolean {
        return activity.findViewById<ViewGroup>(android.R.id.content)
            ?.findViewWithTag<View>(OVERLAY_TAG) != null
    }

    fun closeExisting(activity: Activity, reason: String, onClosed: (String) -> Unit): Boolean {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return false
        val overlay = content.findViewWithTag<View>(OVERLAY_TAG) ?: return false
        close(overlay, reason, onClosed)
        return true
    }

    fun close(overlay: View, reason: String, onClosed: (String) -> Unit) {
        overlay.animate()
            .alpha(0f)
            .setDuration(200L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                onClosed(reason)
            }
            .start()
    }

    fun isBackKey(event: KeyEvent?): Boolean {
        return event?.keyCode == KeyEvent.KEYCODE_BACK
    }
}

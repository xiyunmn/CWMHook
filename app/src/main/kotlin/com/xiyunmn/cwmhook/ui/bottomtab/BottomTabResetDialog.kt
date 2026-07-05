package com.xiyunmn.cwmhook.ui.bottomtab

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.roundRect
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import kotlin.math.min

internal class BottomTabResetDialog(
    private val activity: Activity,
    private val theme: PanelTheme,
) {
    fun show(onConfirm: () -> Unit) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val dialogOverlay = FrameLayout(activity).apply {
            isClickable = true
            setBackgroundColor(0x80000000.toInt())
            alpha = 0f
        }

        val dialog = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(theme.panelBackground, dp(activity, 12).toFloat())
            elevation = dp(activity, 16).toFloat()
            setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 16))
            scaleX = 0.8f
            scaleY = 0.8f
            alpha = 0f
        }

        dialog.addView(
            TextView(activity).apply {
                text = "确认恢复默认？"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        dialog.addView(
            TextView(activity).apply {
                text = "将清除所有自定义设置，恢复为默认底栏配置"
                textSize = 13f
                setTextColor(theme.subText)
                gravity = Gravity.CENTER
                setPadding(0, dp(activity, 8), 0, dp(activity, 20))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancelButton = TextView(activity).apply {
            text = "取消"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.subText)
            gravity = Gravity.CENTER
            background = roundRect(theme.rowBackground, dp(activity, 8).toFloat())
            setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 10))
            isClickable = true
            setOnClickListener {
                close(dialogOverlay)
            }
        }

        val confirmButton = TextView(activity).apply {
            text = "确认恢复"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundRect(theme.accent, dp(activity, 8).toFloat())
            setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 10))
            isClickable = true
            setOnClickListener {
                onConfirm()
                close(dialogOverlay)
            }
        }

        buttonRow.addView(
            cancelButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(activity, 8)
            },
        )
        buttonRow.addView(
            confirmButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 8)
            },
        )

        dialog.addView(
            buttonRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        val dialogParams = FrameLayout.LayoutParams(
            min(dp(activity, 280), content.width - dp(activity, 80)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        dialogOverlay.addView(dialog, dialogParams)
        content.addView(
            dialogOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        dialogOverlay.animate().alpha(1f).setDuration(150L).start()
        dialog.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200L)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
            .start()
    }

    private fun close(dialogOverlay: View) {
        dialogOverlay.animate()
            .alpha(0f)
            .setDuration(150L)
            .withEndAction {
                (dialogOverlay.parent as? ViewGroup)?.removeView(dialogOverlay)
            }
            .start()
    }
}

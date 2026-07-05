package com.xiyunmn.cwmhook.ui.bottomtab

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.ui.common.animatePress
import com.xiyunmn.cwmhook.ui.common.blendColor
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.roundRect
import com.xiyunmn.cwmhook.ui.panel.FloatingPanelDragController
import com.xiyunmn.cwmhook.ui.panel.IconType
import com.xiyunmn.cwmhook.ui.panel.InlineIconView
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.panel.TileIconView

internal class BottomTabEditorChromeFactory(
    private val activity: Activity,
    private val theme: PanelTheme,
    private val state: BottomTabPanelState,
    private val layouts: BottomTabEditorLayouts,
) {
    fun createHeader(panel: View): LinearLayout {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 18), 0, dp(activity, 18), 0)
            setOnTouchListener(FloatingPanelDragController(panel))
        }
        header.addView(
            TextView(activity).apply {
                text = "CWMHook"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
                gravity = Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        return header
    }

    fun createTitle(render: () -> Unit): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = roundRect(
                if (state.expanded) blendColor(theme.accent, theme.rowBackground, 0.08f) else theme.rowBackground,
                dp(activity, 9).toFloat(),
            )
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            setOnClickListener {
                animatePress(this) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    state.expanded = !state.expanded
                    render()
                }
            }
        }
        row.addView(
            TileIconView(activity, IconType.TAB, theme.accent, theme.subIconBackground),
            LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)),
        )
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), 0, dp(activity, 8), 0)
        }
        texts.addView(
            TextView(activity).apply {
                text = "底栏 Tab 自定义"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.accent)
            },
            layouts.matchWrap(),
        )
        texts.addView(
            TextView(activity).apply {
                text = "拖动排序，控制显示/隐藏"
                textSize = 12f
                setTextColor(theme.subText)
            },
            layouts.matchWrap(),
        )
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            TextView(activity).apply {
                text = if (state.expanded) "▲" else "▼"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(theme.accent)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(dp(activity, 32), ViewGroup.LayoutParams.MATCH_PARENT),
        )
        return row
    }

    fun createMoreOptionsTitle(): TextView {
        return TextView(activity).apply {
            text = "更多选项"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.accent)
            setPadding(0, dp(activity, 16), 0, dp(activity, 6))
        }
    }

    fun createHintCard(): LinearLayout {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
            background = roundRect(theme.cardBackground, dp(activity, 9).toFloat())
        }
        card.addView(InlineIconView(activity, IconType.HELP, theme.subIcon), LinearLayout.LayoutParams(dp(activity, 26), dp(activity, 34)))
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 10), 0, 0, 0)
        }
        texts.addView(
            TextView(activity).apply {
                text = "提示"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
            },
            layouts.matchWrap(),
        )
        texts.addView(
            TextView(activity).apply {
                text = "隐藏的 Tab 不会在底栏中显示，保存后立即应用"
                textSize = 11f
                setTextColor(theme.subText)
            },
            layouts.matchWrap(),
        )
        card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return card
    }
}

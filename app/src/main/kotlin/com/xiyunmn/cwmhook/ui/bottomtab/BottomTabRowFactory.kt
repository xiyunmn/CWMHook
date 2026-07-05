package com.xiyunmn.cwmhook.ui.bottomtab

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabSpec
import com.xiyunmn.cwmhook.ui.common.animatePress
import com.xiyunmn.cwmhook.ui.common.blendColor
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.roundRect
import com.xiyunmn.cwmhook.ui.panel.IconType
import com.xiyunmn.cwmhook.ui.panel.InlineIconView
import com.xiyunmn.cwmhook.ui.common.PanelTheme

internal class BottomTabRowFactory(
    private val activity: Activity,
    private val theme: PanelTheme,
    private val state: BottomTabPanelState,
    private val layouts: BottomTabEditorLayouts,
) {
    fun createRows(render: () -> Unit): LinearLayout {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        state.order.forEach { key ->
            val spec = BottomTabConfigStore.tabByKey(key) ?: return@forEach
            container.addView(createTabRow(spec, container, render), layouts.tabRowParams())
        }
        return container
    }

    private fun createTabRow(
        spec: BottomTabSpec,
        container: LinearLayout,
        render: () -> Unit,
    ): LinearLayout {
        val visible = state.visibleKeys.contains(spec.key)
        val rowBackground = if (visible) blendColor(theme.accent, theme.rowBackground, 0.12f) else theme.rowBackground
        val rowStroke = if (visible) theme.accent else theme.separator
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 14), 0, dp(activity, 10), 0)
            background = roundRect(rowBackground, dp(activity, 9).toFloat(), rowStroke)
        }
        val dragListener = BottomTabDragController(row, container, state, spec.key, render)
        val dragArea = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dragHandle = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setOnTouchListener(dragListener)
            addView(
                InlineIconView(activity, IconType.DRAG, if (visible) theme.accent else theme.mutedIcon),
                LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)),
            )
        }
        dragArea.addView(
            dragHandle,
            LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)),
        )
        dragArea.addView(
            InlineIconView(activity, tabIcon(spec.key), if (visible) theme.accent else theme.disabledIcon),
            LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 48)),
        )
        dragArea.addView(
            TextView(activity).apply {
                text = spec.label
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
                setTextColor(if (visible) theme.text else theme.subText)
                setPadding(dp(activity, 16), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        row.addView(dragArea, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(
            InlineIconView(
                activity,
                if (visible) IconType.EYE else IconType.EYE_OFF,
                if (visible) theme.accent else theme.disabledIcon,
            ).apply {
                isClickable = true
                var lastClickTime = 0L
                setOnClickListener {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime < 300L) {
                        return@setOnClickListener
                    }
                    lastClickTime = now
                    animatePress(this) {
                        if (visible && state.visibleKeys.size <= 1) {
                            Toast.makeText(activity, "至少保留一个底栏 Tab", Toast.LENGTH_SHORT).show()
                        } else {
                            this.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                            state.toggleVisible(spec.key)
                            render()
                        }
                    }
                }
            },
            LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 48)),
        )
        return row
    }

    private fun tabIcon(key: String): IconType {
        return when (key) {
            "store" -> IconType.HOME
            "rank" -> IconType.RANK
            "shelf" -> IconType.BOOK
            "find" -> IconType.COMPASS
            "mine" -> IconType.USER
            else -> IconType.TAB
        }
    }
}

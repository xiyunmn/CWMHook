package com.xiyunmn.cwmhook.ui.bottomtab

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.ui.common.animatePress
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.roundRect
import com.xiyunmn.cwmhook.ui.panel.IconType
import com.xiyunmn.cwmhook.ui.panel.InlineIconView
import com.xiyunmn.cwmhook.ui.common.PanelTheme

internal class BottomTabEditorActionFactory(
    private val activity: Activity,
    private val theme: PanelTheme,
    private val state: BottomTabPanelState,
    private val layouts: BottomTabEditorLayouts,
    private val resetDialog: BottomTabResetDialog,
    private val onSave: (BottomTabConfig) -> Unit,
) {
    fun createResetRow(render: () -> Unit): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(activity, 16), 0, dp(activity, 12), 0)
            background = roundRect(theme.rowBackground, dp(activity, 9).toFloat(), theme.separator)
            setOnClickListener {
                animatePress(this) {
                    resetDialog.show {
                        performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        state.reset()
                        render()
                        Toast.makeText(activity, "已恢复默认，保存后生效", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        row.addView(InlineIconView(activity, IconType.RESET, theme.accent), LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 46)))
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), 0, 0, 0)
        }
        texts.addView(
            TextView(activity).apply {
                text = "恢复默认设置"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
            },
            layouts.matchWrap(),
        )
        texts.addView(
            TextView(activity).apply {
                text = "将底栏 Tab 恢复为默认顺序和可见状态"
                textSize = 11f
                setTextColor(theme.subText)
            },
            layouts.matchWrap(),
        )
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    fun createBottomActions(
        render: () -> Unit,
        afterSave: (() -> Unit)? = null,
    ): LinearLayout {
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        footer.addView(createActionButton(IconType.DELETE, "清空模块配置", theme.text) {
            state.reset()
            render()
            Toast.makeText(activity, "已恢复为默认配置，保存后生效", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        footer.addView(View(activity).apply { setBackgroundColor(theme.separator) }, LinearLayout.LayoutParams(1, dp(activity, 28)))
        footer.addView(createActionButton(IconType.POWER, "保存并应用", theme.accent) {
            onSave(state.toConfig())
            afterSave?.invoke()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return footer
    }

    private fun createActionButton(
        icon: IconType,
        label: String,
        color: Int,
        action: () -> Unit,
    ): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animatePress(this) { action() }
            }
            addView(InlineIconView(activity, icon, color), LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)))
            addView(
                TextView(activity).apply {
                    text = label
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(color)
                    setPadding(dp(activity, 8), 0, 0, 0)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
    }
}

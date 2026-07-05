package com.xiyunmn.cwmhook.ui.statusbar

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.animatePress
import com.xiyunmn.cwmhook.ui.common.blendColor
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.roundRect

/**
 * 状态栏设置区域 UI
 *
 * 状态数据和回调由外部传入，不直接依赖 feature 层
 */
internal class StatusBarSettingsSection(
    private val context: Context,
    private val theme: PanelTheme,
) {
    /**
     * 状态栏设置 UI 状态
     */
    data class UiState(
        val enabled: Boolean,
        val expanded: Boolean,
    )

    /**
     * 状态栏设置回调
     */
    interface Callbacks {
        fun onEnabledChanged(enabled: Boolean)
        fun onClearCache()
        fun onReapply()
        fun onExpandedChanged(expanded: Boolean)
    }

    fun createView(state: UiState, callbacks: Callbacks): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 标题行
        container.addView(createTitleRow(state, callbacks))

        // 展开内容
        if (state.expanded) {
            container.addView(createExpandedContent(state, callbacks))
        }

        return container
    }

    private fun createTitleRow(state: UiState, callbacks: Callbacks): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = roundRect(
                if (state.expanded) blendColor(theme.accent, theme.rowBackground, 0.08f) else theme.rowBackground,
                dp(context, 9).toFloat()
            )
            setPadding(
                dp(context, 12),
                dp(context, 10),
                dp(context, 12),
                dp(context, 10)
            )
            setOnClickListener {
                animatePress(this) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    callbacks.onExpandedChanged(!state.expanded)
                }
            }

            // 图标
            addView(
                StatusBarIconView(context, theme.accent, theme.subIconBackground),
                LinearLayout.LayoutParams(dp(context, 42), dp(context, 42))
            )

            // 文字区域
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 14), 0, dp(context, 8), 0)
            }
            texts.addView(TextView(context).apply {
                text = "状态栏背景优化"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.accent)
            })
            texts.addView(TextView(context).apply {
                text = if (state.enabled) "已启用" else "已禁用"
                textSize = 12f
                setTextColor(theme.subText)
            })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            // 展开/收起箭头
            addView(TextView(context).apply {
                text = if (state.expanded) "▲" else "▼"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(theme.accent)
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(dp(context, 32), ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun createExpandedContent(state: UiState, callbacks: Callbacks): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 8), 0, 0)

            // 主开关
            addView(createSwitchRow(
                "启用状态栏优化",
                "透明系统状态栏并自动取色",
                state.enabled
            ) { enabled ->
                callbacks.onEnabledChanged(enabled)
            })

            // 更多选项标题
            addView(TextView(context).apply {
                text = "更多选项"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.accent)
                setPadding(0, dp(context, 16), 0, dp(context, 6))
            })

            // 清除缓存
            addView(createActionRow(
                "清除取色缓存",
                "清理已缓存的页面颜色"
            ) {
                callbacks.onClearCache()
            })

            // 重新应用
            addView(createActionRow(
                "重新应用当前页面",
                "立即刷新当前页状态栏"
            ) {
                callbacks.onReapply()
            })
        }
    }

    private fun createSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(context, 16), 0, dp(context, 12), 0)
            background = roundRect(theme.rowBackground, dp(context, 9).toFloat(), theme.separator)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 8)
            }

            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 12), dp(context, 10), 0, dp(context, 10))
            }
            texts.addView(TextView(context).apply {
                text = title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
            })
            texts.addView(TextView(context).apply {
                text = subtitle
                textSize = 11f
                setTextColor(theme.subText)
            })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            // 简单的开关指示
            val switchView = TextView(context).apply {
                text = if (checked) "✓" else "○"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (checked) theme.accent else theme.mutedIcon)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(context, 12), 0, dp(context, 12), 0)
            }
            addView(switchView, LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)))

            setOnClickListener {
                animatePress(this) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    onToggle(!checked)
                }
            }
        }
    }

    private fun createActionRow(
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(context, 16), 0, dp(context, 12), 0)
            background = roundRect(theme.rowBackground, dp(context, 9).toFloat(), theme.separator)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 8)
            }

            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 12), dp(context, 10), 0, dp(context, 10))
            }
            texts.addView(TextView(context).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
            })
            texts.addView(TextView(context).apply {
                text = subtitle
                textSize = 11f
                setTextColor(theme.subText)
            })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            setOnClickListener {
                animatePress(this) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                }
            }
        }
    }
}

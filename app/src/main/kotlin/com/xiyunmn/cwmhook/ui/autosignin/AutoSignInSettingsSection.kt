package com.xiyunmn.cwmhook.ui.autosignin

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
import com.xiyunmn.cwmhook.ui.panel.IconType
import com.xiyunmn.cwmhook.ui.panel.InlineIconView
import com.xiyunmn.cwmhook.ui.panel.TileIconView

internal class AutoSignInSettingsSection(
    private val context: Context,
    private val theme: PanelTheme,
) {
    data class UiState(
        val enabled: Boolean,
        val expanded: Boolean,
    )

    interface Callbacks {
        fun onEnabledChanged(enabled: Boolean)
        fun onManualSignIn()
        fun onExpandedChanged(expanded: Boolean)
    }

    fun createView(state: UiState, callbacks: Callbacks): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(createTitleRow(state, callbacks))
            if (state.expanded) {
                addView(createExpandedContent(state, callbacks))
            }
        }
    }

    private fun createTitleRow(state: UiState, callbacks: Callbacks): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = roundRect(
                if (state.expanded) blendColor(theme.accent, theme.rowBackground, 0.08f) else theme.rowBackground,
                dp(context, 9).toFloat(),
            )
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            setOnClickListener {
                animatePress(this) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    callbacks.onExpandedChanged(!state.expanded)
                }
            }

            addView(
                TileIconView(context, IconType.POWER, theme.accent, theme.subIconBackground),
                LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)),
            )

            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 14), 0, dp(context, 8), 0)
            }
            texts.addView(TextView(context).apply {
                text = "自动签到"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.accent)
            })
            texts.addView(TextView(context).apply {
                text = if (state.enabled) "已启用" else "已停用"
                textSize = 12f
                setTextColor(theme.subText)
            })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(
                TextView(context).apply {
                    text = if (state.expanded) "▲" else "▼"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(theme.accent)
                    typeface = Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(dp(context, 32), ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    private fun createExpandedContent(state: UiState, callbacks: Callbacks): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 8), 0, 0)
            addView(
                createSwitchRow(
                    title = "启用每日自动签到",
                    subtitle = "应用前台后每日尝试一次",
                    checked = state.enabled,
                    onToggle = callbacks::onEnabledChanged,
                ),
            )
            addView(
                createActionRow(
                    title = "立即签到",
                    subtitle = "手动触发当前账号签到",
                    callbacks = callbacks,
                ),
            )
        }
    }

    private fun createSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(context, 16), 0, dp(context, 12), 0)
            background = roundRect(theme.rowBackground, dp(context, 9).toFloat(), theme.separator)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
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
            addView(
                TextView(context).apply {
                    text = if (checked) "✓" else "○"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(if (checked) theme.accent else theme.mutedIcon)
                    typeface = Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)),
            )
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
        callbacks: Callbacks,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(context, 16), 0, dp(context, 12), 0)
            background = roundRect(theme.rowBackground, dp(context, 9).toFloat(), theme.separator)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(context, 8)
            }

            addView(InlineIconView(context, IconType.POWER, theme.accent), LinearLayout.LayoutParams(dp(context, 32), dp(context, 46)))
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
                    callbacks.onManualSignIn()
                }
            }
        }
    }
}

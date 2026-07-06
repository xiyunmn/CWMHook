package com.xiyunmn.cwmhook.ui.startuptab

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.animatePress
import com.xiyunmn.cwmhook.ui.common.blendColor
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.roundRect
import com.xiyunmn.cwmhook.ui.panel.IconType
import com.xiyunmn.cwmhook.ui.panel.InlineIconView
import com.xiyunmn.cwmhook.ui.panel.TileIconView

internal class StartupTabSettingsSection(
    private val context: Context,
    private val theme: PanelTheme,
) {
    data class UiState(
        val enabled: Boolean,
        val expanded: Boolean,
        val tabKey: String,
    )

    interface Callbacks {
        fun onEnabledChanged(enabled: Boolean)
        fun onTabChanged(tabKey: String)
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
        val selectedLabel = BottomTabConfigStore.tabByKey(state.tabKey)?.label ?: "书城"
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
                TileIconView(context, IconType.HOME, theme.accent, theme.subIconBackground),
                LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)),
            )

            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 14), 0, dp(context, 8), 0)
            }
            texts.addView(TextView(context).apply {
                text = "启动默认 Tab"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.accent)
            })
            texts.addView(TextView(context).apply {
                text = if (state.enabled) "启动时进入：$selectedLabel" else "跟随宿主默认"
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
            addView(createSwitchRow(state, callbacks))
            addView(TextView(context).apply {
                text = "启动目标"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.accent)
                setPadding(0, dp(context, 16), 0, dp(context, 6))
            })
            BottomTabConfigStore.TABS.forEach { tab ->
                addView(
                    createTabRow(
                        label = tab.label,
                        icon = iconFor(tab.key),
                        selected = state.tabKey == tab.key,
                        onClick = { callbacks.onTabChanged(tab.key) },
                    ),
                )
            }
        }
    }

    private fun createSwitchRow(state: UiState, callbacks: Callbacks): LinearLayout {
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
                text = "启用启动默认 Tab"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
            })
            texts.addView(TextView(context).apply {
                text = "仅无深链、无显式跳转时生效"
                textSize = 11f
                setTextColor(theme.subText)
            })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = if (state.enabled) "✓" else "○"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (state.enabled) theme.accent else theme.mutedIcon)
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)))
            setOnClickListener {
                animatePress(this) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    callbacks.onEnabledChanged(!state.enabled)
                }
            }
        }
    }

    private fun createTabRow(
        label: String,
        icon: IconType,
        selected: Boolean,
        onClick: () -> Unit,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(context, 16), 0, dp(context, 12), 0)
            background = roundRect(
                if (selected) blendColor(theme.accent, theme.rowBackground, 0.1f) else theme.rowBackground,
                dp(context, 9).toFloat(),
                if (selected) blendColor(theme.accent, theme.separator, 0.45f) else theme.separator,
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(context, 8)
            }

            addView(
                InlineIconView(context, icon, if (selected) theme.accent else theme.subIcon),
                LinearLayout.LayoutParams(dp(context, 32), dp(context, 46)),
            )
            addView(
                TextView(context).apply {
                    text = label
                    textSize = 13f
                    typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(if (selected) theme.accent else theme.text)
                    setPadding(dp(context, 12), 0, 0, 0)
                    gravity = Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
            )
            addView(TextView(context).apply {
                text = if (selected) "✓" else "○"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (selected) theme.accent else theme.mutedIcon)
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)))
            setOnClickListener {
                animatePress(this) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                }
            }
        }
    }

    private fun iconFor(key: String): IconType {
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

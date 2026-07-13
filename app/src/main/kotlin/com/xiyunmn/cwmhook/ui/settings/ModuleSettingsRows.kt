package com.xiyunmn.cwmhook.ui.settings

import android.app.Activity
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.edgeGlowBackground
import com.xiyunmn.cwmhook.ui.icons.IconType
import com.xiyunmn.cwmhook.ui.icons.InlineIconView

internal class ModuleSettingsRows(
    private val activity: Activity,
    private val theme: PanelTheme,
    private val content: LinearLayout,
) {
    fun addOverviewRow(
        title: String,
        subtitle: String,
        enabled: Boolean?,
        onToggle: (() -> Unit)?,
        onOpen: (() -> Unit)?,
        icon: IconType? = null,
        extraActionIcon: IconType? = null,
        onExtraAction: (() -> Unit)? = null,
        heightDp: Int? = null,
        subtitleMaxLines: Int = 2,
    ) {
        content.addView(
            createBaseRow(title, subtitle, icon, subtitleMaxLines = subtitleMaxLines).apply {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    when {
                        onOpen != null -> onOpen.invoke()
                        onToggle != null -> onToggle.invoke()
                    }
                }
                addOverviewTrailingSlots(
                    extraActionIcon = extraActionIcon,
                    onExtraAction = onExtraAction,
                    enabled = enabled,
                    onToggle = onToggle,
                    hasDisclosure = onOpen != null,
                )
            },
            rowParams(subtitle.isNotBlank(), heightDp),
        )
        content.addView(separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    fun addActionRow(title: String, subtitle: String, icon: IconType? = null, onClick: () -> Unit) {
        content.addView(
            createBaseRow(title, subtitle, icon).apply {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                }
            },
            rowParams(subtitle.isNotBlank()),
        )
        content.addView(separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    fun addChoiceRow(title: String, selected: Boolean, leadingIcon: View? = null, onClick: () -> Unit) {
        content.addView(
            createBaseRow(title, "", leadingIcon = leadingIcon).apply {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                }
                addView(
                    FrameLayout(activity).apply {
                        if (selected) {
                            addView(
                                InlineIconView(activity, IconType.RADIO_SELECTED, theme.accent),
                                FrameLayout.LayoutParams(dp(activity, 36), dp(activity, 36), Gravity.CENTER),
                            )
                        }
                    },
                    LinearLayout.LayoutParams(dp(activity, 54), ViewGroup.LayoutParams.MATCH_PARENT),
                )
            },
            rowParams(hasSubtitle = false),
        )
        content.addView(separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    fun addInfoRow(title: String, subtitle: String) {
        content.addView(createBaseRow(title, subtitle), rowParams(subtitle.isNotBlank()))
        content.addView(separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    fun addSectionTitle(title: String) {
        content.addView(
            TextView(activity).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.accent)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 16), dp(activity, 16), 0, dp(activity, 7))
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 40)),
        )
    }

    private fun createBaseRow(
        title: String,
        subtitle: String,
        icon: IconType? = null,
        leadingIcon: View? = null,
        subtitleMaxLines: Int = 2,
    ): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = edgeGlowBackground(activity, theme.rowBackground, theme.accent)
            setPadding(dp(activity, 16), 0, dp(activity, 12), 0)
            val iconView = leadingIcon ?: icon?.let {
                val iconColor = theme.iconAccent(it)
                InlineIconView(activity, it, iconColor)
            }
            if (iconView != null) {
                addView(
                    iconView,
                    LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    },
                )
            }
            val texts = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                if (iconView != null) {
                    setPadding(dp(activity, 12), 0, 0, 0)
                }
            }
            texts.addView(
                TextView(activity).apply {
                    text = title
                    textSize = 15f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(theme.text)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (subtitle.isBlank()) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            if (subtitle.isNotBlank()) {
                texts.addView(
                    TextView(activity).apply {
                        text = subtitle
                        textSize = 11f
                        setTextColor(theme.subText)
                        maxLines = subtitleMaxLines
                        ellipsize = TextUtils.TruncateAt.END
                        includeFontPadding = false
                        setLineSpacing(0f, 1.05f)
                        setPadding(0, dp(activity, 4), 0, 0)
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            }
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun LinearLayout.addOverviewTrailingSlots(
        extraActionIcon: IconType?,
        onExtraAction: (() -> Unit)?,
        enabled: Boolean?,
        onToggle: (() -> Unit)?,
        hasDisclosure: Boolean,
    ) {
        addActionSlot(extraActionIcon, onExtraAction)
        addSwitchSlot(enabled, onToggle)
        addDisclosureSlot(hasDisclosure)
    }

    private fun LinearLayout.addActionSlot(icon: IconType?, action: (() -> Unit)?) {
        addView(
            FrameLayout(activity).apply {
                if (icon != null && action != null) {
                    isClickable = true
                    addView(
                        InlineIconView(activity, icon, theme.iconAccent(icon)),
                        FrameLayout.LayoutParams(dp(activity, 34), dp(activity, 34), Gravity.CENTER),
                    )
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        action()
                    }
                }
            },
            LinearLayout.LayoutParams(dp(activity, 48), ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    private fun LinearLayout.addSwitchSlot(enabled: Boolean?, onToggle: (() -> Unit)?) {
        addView(
            FrameLayout(activity).apply {
                if (enabled != null) {
                    addView(
                        ModuleSettingsToggleIconView(activity, theme, enabled).apply {
                            isClickable = onToggle != null
                            setOnClickListener {
                                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onToggle?.invoke()
                            }
                        },
                        FrameLayout.LayoutParams(dp(activity, 54), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER),
                    )
                }
            },
            LinearLayout.LayoutParams(dp(activity, 58), ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    private fun LinearLayout.addDisclosureSlot(visible: Boolean) {
        addView(
            FrameLayout(activity).apply {
                if (visible) {
                    addView(
                        InlineIconView(activity, IconType.CHEVRON_RIGHT, theme.chevron),
                        FrameLayout.LayoutParams(dp(activity, 24), dp(activity, 24), Gravity.CENTER),
                    )
                }
            },
            LinearLayout.LayoutParams(dp(activity, 34), ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    private fun rowParams(hasSubtitle: Boolean = true, heightDp: Int? = null): LinearLayout.LayoutParams {
        val height = heightDp ?: if (hasSubtitle) 74 else 62
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, height))
    }

    private fun separator(): View {
        return View(activity).apply {
            setBackgroundColor(theme.separator)
        }
    }
}

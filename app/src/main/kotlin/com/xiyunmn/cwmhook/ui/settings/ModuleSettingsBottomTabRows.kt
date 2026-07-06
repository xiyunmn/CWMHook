package com.xiyunmn.cwmhook.ui.settings

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabSpec
import com.xiyunmn.cwmhook.ui.bottomtab.BottomTabDragController
import com.xiyunmn.cwmhook.ui.bottomtab.BottomTabPanelState
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.edgeGlowBackground
import com.xiyunmn.cwmhook.ui.icons.IconType
import com.xiyunmn.cwmhook.ui.icons.InlineIconView

internal class ModuleSettingsBottomTabRows(
    private val activity: Activity,
    private val theme: PanelTheme,
    private val state: BottomTabPanelState,
    private val onStateChanged: () -> Unit,
    private val onRenderRequested: () -> Unit,
    private val toast: (String) -> Unit,
) {
    private val tabIconFactory = ModuleSettingsTabIconFactory(activity, theme)

    fun createRow(spec: BottomTabSpec, container: LinearLayout): LinearLayout {
        val visible = state.visibleKeys.contains(spec.key)
        val slot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.rowBackground)
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = edgeGlowBackground(activity, theme.rowBackground, theme.accent)
            setPadding(dp(activity, 12), 0, dp(activity, 10), 0)
        }
        val dragHandle = FrameLayout(activity).apply {
            addView(
                InlineIconView(activity, IconType.DRAG, if (visible) theme.accent else theme.mutedIcon),
                FrameLayout.LayoutParams(dp(activity, 38), dp(activity, 38), Gravity.CENTER),
            )
            setOnTouchListener(
                BottomTabDragController(slot, container, state, spec.key) {
                    onStateChanged()
                    onRenderRequested()
                },
            )
        }
        row.addView(dragHandle, LinearLayout.LayoutParams(dp(activity, 46), ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(
            tabIconView(spec, visible),
            LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )
        row.addView(
            TextView(activity).apply {
                text = spec.label
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (visible) theme.text else theme.subText)
                typeface = if (visible) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                includeFontPadding = false
                setPadding(dp(activity, 14), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        row.addView(
            visibilityButton(spec, visible),
            LinearLayout.LayoutParams(dp(activity, 56), ViewGroup.LayoutParams.MATCH_PARENT),
        )
        slot.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        slot.addView(separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        return slot
    }

    fun rowParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56))
    }

    private fun visibilityButton(spec: BottomTabSpec, visible: Boolean): FrameLayout {
        return FrameLayout(activity).apply {
            isClickable = true
            addView(
                InlineIconView(
                    activity,
                    if (visible) IconType.EYE else IconType.EYE_OFF,
                    if (visible) theme.accent else theme.disabledIcon,
                ),
                FrameLayout.LayoutParams(dp(activity, 40), dp(activity, 40), Gravity.CENTER),
            )
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                if (visible && state.visibleKeys.size <= 1) {
                    toast("至少保留一个底栏 Tab")
                    return@setOnClickListener
                }
                state.toggleVisible(spec.key)
                onStateChanged()
                onRenderRequested()
            }
        }
    }

    private fun tabIconView(spec: BottomTabSpec, visible: Boolean): View {
        return tabIconFactory.create(
            spec = spec,
            color = tabIconFactory.activeColor(visible),
            alpha = if (visible) 1f else 0.62f,
        )
    }

    private fun separator(): View {
        return View(activity).apply {
            setBackgroundColor(theme.separator)
        }
    }
}

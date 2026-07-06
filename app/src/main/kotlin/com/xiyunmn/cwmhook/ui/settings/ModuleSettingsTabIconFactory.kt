package com.xiyunmn.cwmhook.ui.settings

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.RadioButton
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabSpec
import com.xiyunmn.cwmhook.feature.bottomtab.BottomTabHostResolver
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.icons.IconType
import com.xiyunmn.cwmhook.ui.icons.InlineIconView

internal class ModuleSettingsTabIconFactory(
    private val activity: Activity,
    private val theme: PanelTheme,
) {
    private val hostButtons: Map<String, RadioButton> by lazy(LazyThreadSafetyMode.NONE) {
        BottomTabHostResolver.resolveButtons(activity).orEmpty()
    }

    fun create(spec: BottomTabSpec, color: Int, alpha: Float = 1f): View {
        val drawable = hostTabDrawable(spec, color)
        if (drawable != null) {
            return ImageView(activity).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                this.alpha = alpha
            }
        }
        return InlineIconView(activity, fallbackTabIcon(spec.key), color).apply {
            this.alpha = alpha
        }
    }

    fun activeColor(active: Boolean): Int {
        return if (active) theme.accent else theme.disabledIcon
    }

    private fun hostTabDrawable(spec: BottomTabSpec, color: Int): Drawable? {
        cloneDrawable(topDrawable(hostButtons[spec.key]), color)?.let { return it }
        val name = when (spec.key) {
            "store" -> "dg_tab_btn_recommend"
            "rank" -> "dg_tab_btn_top2"
            "shelf" -> "dg_tab_btn_top3"
            "find" -> "dg_tab_btn_top4"
            "mine" -> "dg_tab_btn_top5"
            else -> null
        } ?: return null
        val id = activity.resources.getIdentifier(name, "drawable", activity.packageName)
        if (id == 0) {
            return null
        }
        return runCatching {
            activity.resources.getDrawable(id, activity.theme).mutate().apply {
                setTint(color)
            }
        }.getOrNull()
    }

    private fun topDrawable(button: RadioButton?): Drawable? {
        if (button == null) {
            return null
        }
        button.compoundDrawablesRelative.getOrNull(1)?.let { return it }
        return button.compoundDrawables.getOrNull(1)
    }

    private fun cloneDrawable(drawable: Drawable?, color: Int): Drawable? {
        val state = drawable?.constantState ?: return null
        return state.newDrawable(activity.resources).mutate().apply {
            setTint(color)
        }
    }

    private fun fallbackTabIcon(key: String): IconType {
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

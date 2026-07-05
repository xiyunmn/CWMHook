package com.xiyunmn.cwmhook.feature.bottomtab

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import java.util.WeakHashMap

object BottomTabRuntimeApplier {
    private const val TAG = "CWMHook.BottomTab"

    private val appliedStates = WeakHashMap<Activity, String>()

    fun clear(activity: Activity) {
        appliedStates.remove(activity)
    }

    fun apply(activity: Activity, config: BottomTabConfig, reason: String): Boolean {
        val tabGroup = BottomTabHostResolver.findMainTabGroup(activity) ?: return false
        val buttons = BottomTabHostResolver.resolveButtons(activity) ?: return false
        val desired = DesiredBottomTabState.from(config)
        val lastState = appliedStates[activity]
        if (!config.enabled && lastState == null) {
            return false
        }

        val signature = desired.signature(config.enabled, config.version)
        if (lastState == signature && isAlreadyApplied(tabGroup, desired, buttons)) {
            return false
        }

        reorderButtons(tabGroup, desired, buttons)
        ensureSelection(tabGroup, desired, buttons)

        if (config.enabled) {
            appliedStates[activity] = signature
        } else {
            appliedStates.remove(activity)
        }
        ModuleFileLogger.i(
            TAG,
            "apply bottom tab reason=$reason enabled=${config.enabled} version=${config.version} " +
                "visible=${desired.visibleKeys.joinToString(",")} hidden=${desired.hiddenKeys.joinToString(",")}"
        )
        return true
    }

    private fun isAlreadyApplied(
        tabGroup: RadioGroup,
        desired: DesiredBottomTabState,
        buttons: Map<String, RadioButton>,
    ): Boolean {
        val children = (0 until tabGroup.childCount).map { tabGroup.getChildAt(it) }
        val actualKeys = children.mapNotNull { child ->
            buttons.entries.firstOrNull { it.value === child }?.key
        }
        return actualKeys == desired.order &&
            desired.order.all { key ->
                val button = buttons[key] ?: return@all false
                val shouldShow = desired.visibleKeys.contains(key)
                if (shouldShow) button.visibility != View.GONE else button.visibility == View.GONE
            }
    }

    private fun reorderButtons(
        tabGroup: RadioGroup,
        desired: DesiredBottomTabState,
        buttons: Map<String, RadioButton>,
    ) {
        val orderedButtons = desired.order.mapNotNull { buttons[it] }
        orderedButtons.forEach { button ->
            if (button.parent === tabGroup) {
                tabGroup.removeView(button)
            }
        }
        desired.order.forEach { key ->
            val button = buttons[key] ?: return@forEach
            val visible = desired.visibleKeys.contains(key)
            button.visibility = if (visible) View.VISIBLE else View.GONE
            tabGroup.addView(button, RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun ensureSelection(
        tabGroup: RadioGroup,
        desired: DesiredBottomTabState,
        buttons: Map<String, RadioButton>,
    ) {
        val checkedId = tabGroup.checkedRadioButtonId
        val checkedKey = buttons.entries.firstOrNull { it.value.id == checkedId }?.key
        if (checkedKey != null && desired.visibleKeys.contains(checkedKey)) {
            return
        }
        desired.visibleKeys.firstOrNull()?.let { key ->
            buttons[key]?.isChecked = true
        }
    }
}

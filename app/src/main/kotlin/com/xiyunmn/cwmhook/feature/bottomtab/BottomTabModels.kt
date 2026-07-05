package com.xiyunmn.cwmhook.feature.bottomtab

import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore

data class DesiredBottomTabState(
    val order: List<String>,
    val visibleKeys: Set<String>,
    val hiddenKeys: Set<String>,
) {
    fun signature(enabled: Boolean, version: Int): String {
        return "$enabled:$version:${order.joinToString(",")}:${hiddenKeys.sorted().joinToString(",")}"
    }

    companion object {
        fun from(config: BottomTabConfig): DesiredBottomTabState {
            val order = if (config.enabled) config.order else BottomTabConfigStore.DEFAULT_ORDER
            val hidden = if (config.enabled) config.hidden else emptySet()
            val visible = order.filterNot { hidden.contains(it) }
            val safeVisible = visible.ifEmpty { listOf(BottomTabConfigStore.DEFAULT_ORDER.first()) }
            return DesiredBottomTabState(order, safeVisible.toSet(), hidden)
        }
    }
}

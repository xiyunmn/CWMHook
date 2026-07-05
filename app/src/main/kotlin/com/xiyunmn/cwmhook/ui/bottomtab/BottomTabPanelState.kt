package com.xiyunmn.cwmhook.ui.bottomtab

import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore

class BottomTabPanelState(
    val order: MutableList<String>,
    val visibleKeys: MutableSet<String>,
    private val baseVersion: Int,
) {
    var expanded: Boolean = true

    fun toggleVisible(key: String) {
        if (visibleKeys.contains(key)) {
            visibleKeys.remove(key)
        } else {
            visibleKeys.add(key)
        }
    }

    fun move(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) {
            return false
        }
        val key = order.removeAt(fromIndex)
        order.add(toIndex, key)
        return true
    }

    fun reset() {
        order.clear()
        order.addAll(BottomTabConfigStore.DEFAULT_ORDER)
        visibleKeys.clear()
        visibleKeys.addAll(BottomTabConfigStore.DEFAULT_ORDER)
    }

    fun toConfig(): BottomTabConfig {
        val hidden = BottomTabConfigStore.DEFAULT_ORDER.filterNot { visibleKeys.contains(it) }.toSet()
        return BottomTabConfig(
            enabled = true,
            order = order.toList(),
            hidden = hidden,
            version = if (baseVersion == Int.MAX_VALUE) 1 else baseVersion + 1,
        )
    }

    companion object {
        fun from(config: BottomTabConfig): BottomTabPanelState {
            val order = config.order.toMutableList()
            val visible = config.order.filterNot { config.hidden.contains(it) }.toMutableSet()
            if (visible.isEmpty()) {
                visible.add(BottomTabConfigStore.DEFAULT_ORDER.first())
            }
            return BottomTabPanelState(order, visible, config.version)
        }
    }
}

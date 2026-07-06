package com.xiyunmn.cwmhook.config

import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore

data class SettingsSnapshot(
    val bottomTab: BottomTabConfig,
    val reserved: ReservedSettings = ReservedSettings(),
) {
    companion object {
        fun default(): SettingsSnapshot {
            return SettingsSnapshot(
                bottomTab = BottomTabConfigStore.defaultConfig(),
            )
        }
    }
}

data class ReservedSettings(
    val statusBar: Boolean = true,
    val fileLogging: Boolean = false,
)

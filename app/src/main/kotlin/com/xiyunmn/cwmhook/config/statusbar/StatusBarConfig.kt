package com.xiyunmn.cwmhook.config.statusbar

/**
 * 状态栏背景优化配置模型
 */
data class StatusBarConfig(
    val enabled: Boolean,
    val version: Int,
) {
    companion object {
        const val DEFAULT_ENABLED = false
        const val DEFAULT_VERSION = 0
    }
}

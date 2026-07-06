package com.xiyunmn.cwmhook.config.startuptab

import android.content.Context
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore

data class StartupTabConfig(
    val enabled: Boolean,
    val tabKey: String,
    val version: Int,
)

object StartupTabConfigStore {
    private const val PREF = "cwmhook_startup_tab"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TAB_KEY = "tab_key"
    private const val KEY_VERSION = "version"

    fun defaultConfig(): StartupTabConfig {
        return StartupTabConfig(
            enabled = false,
            tabKey = BottomTabConfigStore.DEFAULT_ORDER.first(),
            version = 0,
        )
    }

    fun readLocal(context: Context): StartupTabConfig {
        val prefs = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sanitize(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            tabKey = prefs.getString(KEY_TAB_KEY, null),
            version = prefs.getInt(KEY_VERSION, 0),
        )
    }

    fun writeLocal(context: Context, config: StartupTabConfig): Boolean {
        val sanitized = sanitize(config.enabled, config.tabKey, config.version)
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, sanitized.enabled)
            .putString(KEY_TAB_KEY, sanitized.tabKey)
            .putInt(KEY_VERSION, sanitized.version)
            .commit()
    }

    fun nextVersion(config: StartupTabConfig): Int {
        return if (config.version == Int.MAX_VALUE) 1 else config.version + 1
    }

    fun effectiveTabKey(config: StartupTabConfig, bottomTabConfig: BottomTabConfig): String? {
        if (!config.enabled) {
            return null
        }
        val sanitized = sanitize(config.enabled, config.tabKey, config.version)
        val visibleKeys = visibleTabKeys(bottomTabConfig)
        return if (visibleKeys.contains(sanitized.tabKey)) {
            sanitized.tabKey
        } else {
            visibleKeys.firstOrNull()
        }
    }

    private fun sanitize(enabled: Boolean, tabKey: String?, version: Int): StartupTabConfig {
        val safeKey = tabKey
            ?.takeIf { key -> BottomTabConfigStore.tabByKey(key) != null }
            ?: BottomTabConfigStore.DEFAULT_ORDER.first()
        return StartupTabConfig(
            enabled = enabled,
            tabKey = safeKey,
            version = version.coerceAtLeast(0),
        )
    }

    private fun visibleTabKeys(config: BottomTabConfig): List<String> {
        if (!config.enabled) {
            return BottomTabConfigStore.DEFAULT_ORDER
        }
        val visible = config.order.filterNot { key -> config.hidden.contains(key) }
        return visible.ifEmpty { listOf(BottomTabConfigStore.DEFAULT_ORDER.first()) }
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}

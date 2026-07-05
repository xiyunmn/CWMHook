package com.xiyunmn.cwmhook.config.bottomtab

import android.content.Context
import android.content.SharedPreferences

data class BottomTabSpec(
    val key: String,
    val index: Int,
    val buttonName: String,
    val label: String,
)

data class BottomTabConfig(
    val enabled: Boolean,
    val order: List<String>,
    val hidden: Set<String>,
    val version: Int,
)

object BottomTabConfigStore {
    const val PREF_NAME = "bottom_tab"
    private const val LEGACY_LOCAL_PREF = "bottom_tab_local"
    const val KEY_ENABLED = "enabled"
    const val KEY_ORDER = "order"
    const val KEY_HIDDEN = "hidden"
    const val KEY_VERSION = "version"

    val TABS = listOf(
        BottomTabSpec("store", 0, "tabbtn0", "书城"),
        BottomTabSpec("rank", 1, "tabbtn1", "排行"),
        BottomTabSpec("shelf", 2, "tabbtn2", "读书"),
        BottomTabSpec("find", 3, "tabbtn3", "发现"),
        BottomTabSpec("mine", 4, "tabbtn4", "我的"),
    )

    val DEFAULT_ORDER = TABS.map { it.key }

    private val tabKeys = DEFAULT_ORDER.toSet()

    fun read(prefs: SharedPreferences?): BottomTabConfig {
        if (prefs == null) {
            return defaultConfig()
        }
        return sanitize(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            rawOrder = prefs.getString(KEY_ORDER, null),
            rawHidden = prefs.getString(KEY_HIDDEN, null),
            version = prefs.getInt(KEY_VERSION, 0),
        )
    }

    fun write(editor: SharedPreferences.Editor, config: BottomTabConfig): Boolean {
        val sanitized = sanitize(config.enabled, config.order.joinToString(","), config.hidden.joinToString(","), config.version)
        return editor
            .putBoolean(KEY_ENABLED, sanitized.enabled)
            .putString(KEY_ORDER, sanitized.order.joinToString(","))
            .putString(KEY_HIDDEN, sanitized.hidden.joinToString(","))
            .putInt(KEY_VERSION, sanitized.version)
            .commit()
    }

    fun defaultConfig(): BottomTabConfig {
        return BottomTabConfig(
            enabled = false,
            order = DEFAULT_ORDER,
            hidden = emptySet(),
            version = 0,
        )
    }

    fun nextVersion(config: BottomTabConfig): Int {
        return if (config.version == Int.MAX_VALUE) 1 else config.version + 1
    }

    fun tabByKey(key: String): BottomTabSpec? {
        return TABS.firstOrNull { it.key == key }
    }

    fun isConfigKey(key: String?): Boolean {
        return key == null || key == KEY_ENABLED || key == KEY_ORDER || key == KEY_HIDDEN || key == KEY_VERSION
    }

    fun hasStoredConfig(prefs: SharedPreferences?): Boolean {
        return prefs?.contains(KEY_ENABLED) == true ||
            prefs?.contains(KEY_ORDER) == true ||
            prefs?.contains(KEY_HIDDEN) == true ||
            prefs?.contains(KEY_VERSION) == true
    }

    fun readLocal(context: Context): BottomTabConfig {
        val app = context.applicationContext ?: context
        val prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (hasStoredConfig(prefs)) {
            return read(prefs)
        }
        val legacyPrefs = app.getSharedPreferences(LEGACY_LOCAL_PREF, Context.MODE_PRIVATE)
        if (!hasStoredConfig(legacyPrefs)) {
            return defaultConfig()
        }
        val legacyConfig = read(legacyPrefs)
        write(prefs.edit(), legacyConfig)
        legacyPrefs.edit().clear().apply()
        return legacyConfig
    }

    fun writeLocal(context: Context, config: BottomTabConfig): Boolean {
        val app = context.applicationContext ?: context
        val persisted = write(
            app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit(),
            config,
        )
        if (persisted) {
            app.getSharedPreferences(LEGACY_LOCAL_PREF, Context.MODE_PRIVATE).edit().clear().apply()
        }
        return persisted
    }

    private fun sanitize(
        enabled: Boolean,
        rawOrder: String?,
        rawHidden: String?,
        version: Int,
    ): BottomTabConfig {
        val parsedOrder = rawOrder
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { tabKeys.contains(it) }
            .orEmpty()

        val order = (parsedOrder + DEFAULT_ORDER)
            .distinct()
            .filter { tabKeys.contains(it) }

        val hidden = rawHidden
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { tabKeys.contains(it) }
            ?.toSet()
            .orEmpty()
            .let { if (order.all(it::contains)) emptySet() else it }

        return BottomTabConfig(
            enabled = enabled,
            order = order,
            hidden = hidden,
            version = version.coerceAtLeast(0),
        )
    }
}

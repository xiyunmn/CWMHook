package com.xiyunmn.cwmhook.config.debug

import android.content.Context

data class DebugConfig(
    val detailedFileLogEnabled: Boolean,
    val version: Int,
)

object DebugConfigStore {
    private const val PREF = "cwmhook_debug"
    private const val KEY_DETAILED_FILE_LOG = "detailed_file_log"
    private const val KEY_VERSION = "version"

    fun defaultConfig(): DebugConfig {
        return DebugConfig(detailedFileLogEnabled = false, version = 0)
    }

    fun readLocal(context: Context): DebugConfig {
        val defaults = defaultConfig()
        val prefs = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return DebugConfig(
            detailedFileLogEnabled = prefs.getBoolean(KEY_DETAILED_FILE_LOG, defaults.detailedFileLogEnabled),
            version = prefs.getInt(KEY_VERSION, defaults.version).coerceAtLeast(0),
        )
    }

    fun writeLocal(context: Context, config: DebugConfig): Boolean {
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DETAILED_FILE_LOG, config.detailedFileLogEnabled)
            .putInt(KEY_VERSION, config.version.coerceAtLeast(0))
            .commit()
    }

    fun nextVersion(config: DebugConfig): Int {
        return if (config.version == Int.MAX_VALUE) 1 else config.version + 1
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}

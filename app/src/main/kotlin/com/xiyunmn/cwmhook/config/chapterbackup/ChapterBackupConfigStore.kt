package com.xiyunmn.cwmhook.config.chapterbackup

import android.content.Context

data class ChapterBackupConfig(
    val enabled: Boolean,
    val version: Int,
    val exportTreeUri: String?,
)

object ChapterBackupConfigStore {
    private const val PREF = "cwmhook_chapter_export"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VERSION = "version"
    private const val KEY_EXPORT_TREE_URI = "export_tree_uri"

    fun defaultConfig(): ChapterBackupConfig {
        return ChapterBackupConfig(enabled = false, version = 0, exportTreeUri = null)
    }

    fun readLocal(context: Context): ChapterBackupConfig {
        val prefs = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return ChapterBackupConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            version = prefs.getInt(KEY_VERSION, 0),
            exportTreeUri = prefs.getString(KEY_EXPORT_TREE_URI, null)?.takeIf { it.isNotBlank() },
        )
    }

    fun writeLocal(context: Context, config: ChapterBackupConfig): Boolean {
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_VERSION, config.version.coerceAtLeast(0))
            .putString(KEY_EXPORT_TREE_URI, config.exportTreeUri?.takeIf { it.isNotBlank() })
            .commit()
    }

    fun nextVersion(config: ChapterBackupConfig): Int {
        return if (config.version == Int.MAX_VALUE) 1 else config.version + 1
    }

    fun rememberExportTreeUri(context: Context, treeUri: String): Boolean {
        val current = readLocal(context)
        return writeLocal(
            context,
            current.copy(
                exportTreeUri = treeUri,
                version = nextVersion(current),
            ),
        )
    }

    fun clearExportTreeUri(context: Context): Boolean {
        val current = readLocal(context)
        return writeLocal(
            context,
            current.copy(
                exportTreeUri = null,
                version = nextVersion(current),
            ),
        )
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}

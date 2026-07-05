package com.xiyunmn.cwmhook.config.readerfont

import android.content.Context
import java.io.File

data class ReaderFontConfig(
    val enabled: Boolean,
    val version: Int,
)

object ReaderFontConfigStore {
    private const val PREF = "cwmhook_reader_font"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VERSION = "version"
    private const val KEY_FONTS = "fonts"

    fun defaultConfig(): ReaderFontConfig {
        return ReaderFontConfig(enabled = false, version = 0)
    }

    fun readLocal(context: Context): ReaderFontConfig {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return ReaderFontConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            version = prefs.getInt(KEY_VERSION, 0),
        )
    }

    fun writeLocal(context: Context, config: ReaderFontConfig): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_VERSION, config.version.coerceAtLeast(0))
            .commit()
    }

    fun isEnabled(context: Context): Boolean {
        return readLocal(context).enabled
    }

    fun readFonts(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_FONTS, "") ?: ""
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    fun rememberFonts(context: Context, paths: List<String>) {
        val normalized = paths.map { File(it).absolutePath }
        val fonts = buildList {
            addAll(readFonts(context).filterNot { it in normalized })
            addAll(normalized)
        }
        writeFonts(context, fonts)
    }

    fun writeFonts(context: Context, paths: List<String>): Boolean {
        val normalized = paths.asSequence()
            .map { File(it).absolutePath }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FONTS, normalized.joinToString("\n"))
            .commit()
    }
}

package com.xiyunmn.cwmhook.config.bookshelf

import android.content.Context

data class BookshelfConfig(
    val hideContinueReadingCard: Boolean,
    val version: Int,
)

object BookshelfConfigStore {
    private const val PREF = "cwmhook_bookshelf"
    private const val KEY_HIDE_CONTINUE_READING_CARD = "hide_continue_reading_card"
    private const val KEY_VERSION = "version"

    fun defaultConfig(): BookshelfConfig {
        return BookshelfConfig(hideContinueReadingCard = false, version = 0)
    }

    fun readLocal(context: Context): BookshelfConfig {
        val defaults = defaultConfig()
        val prefs = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return BookshelfConfig(
            hideContinueReadingCard = prefs.getBoolean(
                KEY_HIDE_CONTINUE_READING_CARD,
                defaults.hideContinueReadingCard,
            ),
            version = prefs.getInt(KEY_VERSION, defaults.version).coerceAtLeast(0),
        )
    }

    fun writeLocal(context: Context, config: BookshelfConfig): Boolean {
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_CONTINUE_READING_CARD, config.hideContinueReadingCard)
            .putInt(KEY_VERSION, config.version.coerceAtLeast(0))
            .commit()
    }

    fun nextVersion(config: BookshelfConfig): Int {
        return if (config.version == Int.MAX_VALUE) 1 else config.version + 1
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}

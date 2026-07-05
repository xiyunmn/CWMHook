package com.xiyunmn.cwmhook.config.autosignin

import android.content.Context
import java.time.LocalDate

data class AutoSignInConfig(
    val enabled: Boolean,
    val version: Int,
)

object AutoSignInConfigStore {
    private const val PREF = "cwmhook_auto_sign_in"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VERSION = "version"
    private const val KEY_LAST_ATTEMPT_PREFIX = "last_attempt_date_"
    private const val KEY_LAST_SUCCESS_PREFIX = "last_success_date_"
    private const val KEY_LAST_RESULT_PREFIX = "last_result_"

    fun defaultConfig(): AutoSignInConfig {
        return AutoSignInConfig(enabled = false, version = 0)
    }

    fun readLocal(context: Context): AutoSignInConfig {
        val prefs = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return AutoSignInConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            version = prefs.getInt(KEY_VERSION, 0),
        )
    }

    fun writeLocal(context: Context, config: AutoSignInConfig): Boolean {
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_VERSION, config.version.coerceAtLeast(0))
            .commit()
    }

    fun nextVersion(config: AutoSignInConfig): Int {
        return if (config.version == Int.MAX_VALUE) 1 else config.version + 1
    }

    fun today(): String {
        return LocalDate.now().toString()
    }

    fun hasAttemptedToday(context: Context, readerId: String, today: String = today()): Boolean {
        val prefs = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_ATTEMPT_PREFIX + readerId, "") == today
    }

    fun recordResult(
        context: Context,
        readerId: String,
        date: String,
        success: Boolean,
        message: String,
    ): Boolean {
        val editor = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ATTEMPT_PREFIX + readerId, date)
            .putString(KEY_LAST_RESULT_PREFIX + readerId, message)
        if (success) {
            editor.putString(KEY_LAST_SUCCESS_PREFIX + readerId, date)
        }
        return editor.commit()
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}
